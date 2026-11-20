<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\StoreKit\SignedTransaction;
use App\Services\StoreKit\TransactionVerifier;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

class AppStoreWebhookTest extends TestCase
{
    use RefreshDatabase;

    /**
     * Reuses FakeTransactionVerifier from SubscriptionTest (same namespace).
     * Bound once per test, then updated in place like SubscriptionTest.
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

    private function signedNotification(string $notificationType, ?string $signedTransactionInfo = null): string
    {
        $payload = rtrim(strtr(base64_encode(json_encode([
            'notificationType' => $notificationType,
            'data' => ['signedTransactionInfo' => $signedTransactionInfo ?? SubscriptionTest::JWS],
        ])), '+/', '-_'), '=');

        return 'eyJhbGciOiJFUzI1NiJ9.'.$payload.'.c2ln';
    }

    public function test_did_renew_and_subscribed_extend_pro(): void
    {
        $this->fakeVerifier(['expiresDate' => Carbon::parse('2031-06-15T12:00:00Z')]);
        $user = User::factory()->create([
            'pro_until' => now()->addDay(),
            'pro_original_transaction_id' => '2000000123456789',
        ]);

        $this->postJson('/api/v1/webhooks/app-store', [
            'signedPayload' => $this->signedNotification('DID_RENEW'),
        ])->assertOk()->assertJsonPath('ok', true);

        $this->assertSame('2031-06-15T12:00:00Z', $user->fresh()?->pro_until?->toIso8601ZuluString());

        $this->fakeVerifier(['expiresDate' => Carbon::parse('2032-01-01T00:00:00Z')]);
        $this->postJson('/api/v1/webhooks/app-store', [
            'signedPayload' => $this->signedNotification('SUBSCRIBED'),
        ])->assertOk()->assertJsonPath('ok', true);

        $this->assertSame('2032-01-01T00:00:00Z', $user->fresh()?->pro_until?->toIso8601ZuluString());
    }

    public function test_expired_and_revoke_clear_pro(): void
    {
        $this->fakeVerifier();
        $user = User::factory()->create([
            'pro_until' => now()->addMonth(),
            'pro_original_transaction_id' => '2000000123456789',
        ]);

        $this->postJson('/api/v1/webhooks/app-store', [
            'signedPayload' => $this->signedNotification('EXPIRED'),
        ])->assertOk()->assertJsonPath('ok', true);

        $this->assertNull($user->fresh()?->pro_until);
        $this->assertNull($user->fresh()?->pro_original_transaction_id);

        $user->forceFill([
            'pro_until' => now()->addMonth(),
            'pro_original_transaction_id' => '2000000123456789',
        ])->save();

        $this->postJson('/api/v1/webhooks/app-store', [
            'signedPayload' => $this->signedNotification('REVOKE'),
        ])->assertOk()->assertJsonPath('ok', true);

        $this->assertNull($user->fresh()?->pro_until);
    }

    public function test_missing_signed_payload_is_401(): void
    {
        $this->postJson('/api/v1/webhooks/app-store', [])
            ->assertUnauthorized()
            ->assertJsonPath('message', 'Missing signedPayload.');

        $this->postJson('/api/v1/webhooks/app-store', ['signedPayload' => ''])
            ->assertUnauthorized()
            ->assertJsonPath('message', 'Missing signedPayload.');
    }

    public function test_unmatched_original_transaction_id_is_still_ok(): void
    {
        $this->fakeVerifier(['originalTransactionId' => '9999999999999999']);
        User::factory()->create([
            'pro_until' => now()->addMonth(),
            'pro_original_transaction_id' => '2000000123456789',
        ]);

        $this->postJson('/api/v1/webhooks/app-store', [
            'signedPayload' => $this->signedNotification('REFUND'),
        ])->assertOk()->assertJsonPath('ok', true);

        $this->assertNotNull(User::query()->where('pro_original_transaction_id', '2000000123456789')->value('pro_until'));
    }
}
