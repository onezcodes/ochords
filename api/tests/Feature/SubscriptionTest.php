<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\StoreKit\AppleTransactionVerifier;
use App\Services\StoreKit\MalformedTransactionException;
use App\Services\StoreKit\SignedTransaction;
use App\Services\StoreKit\TransactionSignatureException;
use App\Services\StoreKit\TransactionVerifier;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

class SubscriptionTest extends TestCase
{
    use RefreshDatabase;

    public const JWS = 'eyJhbGciOiJFUzI1NiJ9.eyJwcm9kdWN0SWQiOiJ4In0.c2ln';

    /**
     * Stands in for Apple's signature check: JWS "good" decodes to the given claims, anything else fails.
     * Bound once per test (the route keeps its controller between requests), then updated in place.
     */
    private function fakeVerifier(array $claims = []): void
    {
        $transaction = new SignedTransaction(...array_merge([
            'bundleId' => 'com.onezcodes.ochords',
            'productId' => 'com.onezcodes.ochords.pro.monthly',
            'originalTransactionId' => '2000000123456789',
            'transactionId' => '2000000123456790',
            'environment' => 'Production',
            'expiresDate' => Carbon::parse('2030-01-01T00:00:00Z'),
            'revocationDate' => null,
            'signedDate' => now(),
        ], $claims));

        $bound = $this->app->bound(TransactionVerifier::class) ? $this->app->make(TransactionVerifier::class) : null;
        if ($bound instanceof FakeTransactionVerifier) {
            $bound->transaction = $transaction;

            return;
        }
        $this->app->instance(TransactionVerifier::class, new FakeTransactionVerifier($transaction));
    }

    public function test_profile_says_whether_the_player_is_pro(): void
    {
        $user = User::factory()->create();
        $this->actingAs($user)->getJson('/api/v1/me')->assertJsonPath('isPro', false);

        $user->refresh()->forceFill(['pro_until' => now()->addDay()])->save();
        $this->actingAs($user)->getJson('/api/v1/me')->assertJsonPath('isPro', true);

        $user->refresh()->forceFill(['pro_until' => now()->subMinute()])->save();
        $this->actingAs($user)->getJson('/api/v1/me')->assertJsonPath('isPro', false);
    }

    public function test_syncing_a_transaction_makes_the_player_pro_until_it_expires(): void
    {
        $this->fakeVerifier();
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])
            ->assertOk()
            ->assertJsonPath('isPro', true)
            ->assertJsonStructure(['user', 'email', 'points', 'week', 'isPro']);

        $fresh = $user->fresh();
        $this->assertSame('2030-01-01T00:00:00Z', $fresh?->pro_until?->toIso8601ZuluString());
        $this->assertSame('2000000123456789', $fresh?->pro_original_transaction_id);
    }

    public function test_an_expired_or_revoked_transaction_is_stored_but_not_pro(): void
    {
        $this->fakeVerifier(['revocationDate' => now()->subHour()]);
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])->assertOk()->assertJsonPath('isPro', false);
    }

    public function test_clearing_the_subscription(): void
    {
        $user = User::factory()->create();
        $user->forceFill(['pro_until' => now()->addDay(), 'pro_original_transaction_id' => '1'])->save();

        $this->actingAs($user)->deleteJson('/api/v1/me/subscription')->assertOk()->assertJsonPath('isPro', false);

        $this->assertNull($user->fresh()?->pro_until);
        $this->assertNull($user->fresh()?->pro_original_transaction_id);
    }

    public function test_a_transaction_apple_did_not_sign_is_401(): void
    {
        $this->fakeVerifier();

        $this->actingAs(User::factory()->create())->putJson('/api/v1/me/subscription', ['transactionJWS' => 'eyJ.forged.sig'])
            ->assertUnauthorized()->assertJsonStructure(['message']);
    }

    public function test_malformed_or_unsupported_transactions_are_422(): void
    {
        $this->fakeVerifier();
        $user = User::factory()->create();
        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => 'not a jws'])
            ->assertUnprocessable()->assertJsonValidationErrors('transactionJWS');

        $this->fakeVerifier(['bundleId' => 'com.example.other']);
        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])
            ->assertUnprocessable()->assertJsonPath('message', 'This transaction is for another app.')->assertJsonValidationErrors('transactionJWS');

        $this->fakeVerifier(['productId' => 'com.onezcodes.ochords.stickers']);
        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])->assertUnprocessable();

        $this->assertNull($user->fresh()?->pro_until);
    }

    public function test_sandbox_transactions_are_only_accepted_when_allowed(): void
    {
        $this->fakeVerifier(['environment' => 'Sandbox']);
        $user = User::factory()->create();

        config(['services.apple.allow_sandbox_receipts' => false]);
        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])->assertUnprocessable();

        config(['services.apple.allow_sandbox_receipts' => true]);
        $this->actingAs($user)->putJson('/api/v1/me/subscription', ['transactionJWS' => self::JWS])->assertOk()->assertJsonPath('isPro', true);
    }

    public function test_the_real_verifier_loads_apples_root_and_rejects_junk(): void
    {
        $verifier = $this->app->make(TransactionVerifier::class);
        $this->assertInstanceOf(AppleTransactionVerifier::class, $verifier);

        $header = rtrim(strtr(base64_encode('{"alg":"ES256","x5c":["AAAA"]}'), '+/', '-_'), '=');
        $payload = rtrim(strtr(base64_encode('{"productId":"x"}'), '+/', '-_'), '=');
        try {
            $verifier->verify("{$header}.{$payload}.c2ln");
            $this->fail('A bogus chain must not verify.');
        } catch (MalformedTransactionException|TransactionSignatureException $e) {
            $this->assertNotSame('', $e->getMessage());
        }
        $this->expectException(MalformedTransactionException::class);
        $verifier->verify('eyJhbGciOiJSUzI1NiJ9.e30.c2ln');
    }
}

class FakeTransactionVerifier implements TransactionVerifier
{
    public function __construct(public SignedTransaction $transaction) {}

    public function verify(string $jws): SignedTransaction
    {
        return match ($jws) {
            SubscriptionTest::JWS => $this->transaction,
            default => throw new TransactionSignatureException('This transaction isn’t signed by Apple.'),
        };
    }
}
