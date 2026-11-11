<?php

namespace Tests\Feature;

use App\Enums\SocialProvider;
use App\Models\User;
use App\Services\Social\InvalidSocialTokenException;
use App\Services\Social\SocialIdentity;
use App\Services\Social\SocialTokenVerifier;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SocialAuthTest extends TestCase
{
    use RefreshDatabase;

    /** Stands in for Apple/Google: "good-apple" / "good-google" verify, anything else is rejected. */
    private function fakeVerifier(?string $email = 'relay@privaterelay.appleid.com', bool $verified = true): void
    {
        $this->app->instance(SocialTokenVerifier::class, new class($email, $verified) implements SocialTokenVerifier
        {
            public function __construct(private readonly ?string $email, private readonly bool $verified) {}

            public function verifyApple(string $identityToken): SocialIdentity
            {
                return $this->identity($identityToken, 'good-apple', SocialProvider::Apple, 'apple-sub-1');
            }

            public function verifyGoogle(string $idToken): SocialIdentity
            {
                return $this->identity($idToken, 'good-google', SocialProvider::Google, 'google-sub-1');
            }

            private function identity(string $token, string $expected, SocialProvider $provider, string $subject): SocialIdentity
            {
                if ($token !== $expected) {
                    throw new InvalidSocialTokenException('This '.ucfirst($provider->value).' sign-in could not be verified.');
                }

                return new SocialIdentity($provider, $subject, $this->email, $this->verified);
            }
        });
    }

    public function test_apple_creates_an_account_from_the_token(): void
    {
        $this->fakeVerifier();

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple', 'name' => 'Aung Aung', 'device_name' => 'iPhone'])
            ->assertCreated()
            ->assertJsonStructure(['token', 'profile' => ['user' => ['id', 'name', 'handle'], 'email', 'isPro']])
            ->assertJsonPath('profile.user.name', 'Aung Aung')
            ->assertJsonPath('profile.email', 'relay@privaterelay.appleid.com')
            ->assertJsonPath('profile.isPro', false);

        $user = User::query()->firstOrFail();
        $this->assertSame('apple-sub-1', $user->apple_id);
        $this->assertSame('aungaung', $user->handle);
    }

    public function test_apple_without_a_name_calls_the_player_guitarist(): void
    {
        $this->fakeVerifier();

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple'])->assertCreated()->assertJsonPath('profile.user.name', 'Guitarist');
    }

    public function test_apple_signs_in_a_linked_account_by_subject(): void
    {
        $this->fakeVerifier(email: 'other@example.com');
        $user = User::factory()->create(['email' => 'me@example.com']);
        $user->forceFill(['apple_id' => 'apple-sub-1', 'deletion_requested_at' => now()])->save();

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple'])
            ->assertOk()
            ->assertJsonPath('profile.user.id', (string) $user->id)
            ->assertJsonPath('profile.email', 'me@example.com');

        $this->assertSame(1, User::query()->count());
        $this->assertNull($user->fresh()?->deletion_requested_at);
    }

    public function test_apple_links_an_existing_account_by_email(): void
    {
        $this->fakeVerifier(email: 'Me@Example.com');
        $user = User::factory()->create(['email' => 'me@example.com']);

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple'])->assertOk()->assertJsonPath('profile.user.id', (string) $user->id);

        $this->assertSame('apple-sub-1', $user->fresh()?->apple_id);
        $this->assertSame(1, User::query()->count());
    }

    public function test_an_unverified_email_does_not_link_an_account(): void
    {
        $this->fakeVerifier(email: 'me@example.com', verified: false);
        User::factory()->create(['email' => 'me@example.com']);

        // Can't take over the account by email; and can't create a duplicate either, so it's a validation error.
        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple'])->assertUnprocessable();
    }

    public function test_a_bad_token_is_401(): void
    {
        $this->fakeVerifier();

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'forged'])->assertUnauthorized()->assertJsonStructure(['message']);
        $this->postJson('/api/v1/auth/google', ['idToken' => 'forged'])->assertUnauthorized()->assertJsonStructure(['message']);
        $this->postJson('/api/v1/auth/apple', [])->assertUnprocessable()->assertJsonValidationErrors('identityToken');
        $this->assertSame(0, User::query()->count());
    }

    public function test_google_creates_links_and_signs_in(): void
    {
        $this->fakeVerifier(email: 'me@gmail.com');

        $this->postJson('/api/v1/auth/google', ['idToken' => 'good-google'])->assertCreated()->assertJsonPath('profile.email', 'me@gmail.com');
        $this->postJson('/api/v1/auth/google', ['idToken' => 'good-google'])->assertOk();

        $this->assertSame(1, User::query()->count());
        $this->assertSame('google-sub-1', User::query()->firstOrFail()->google_id);
    }

    public function test_google_is_503_until_a_client_id_is_configured(): void
    {
        config(['services.google.client_id' => null]);

        $this->postJson('/api/v1/auth/google', ['idToken' => 'anything'])
            ->assertServiceUnavailable()
            ->assertJsonPath('message', 'Google sign-in isn’t set up yet.');
    }

    public function test_social_sign_in_is_rate_limited_like_auth(): void
    {
        $this->fakeVerifier();
        for ($i = 0; $i < 10; $i++) {
            $this->postJson('/api/v1/auth/apple', ['identityToken' => 'forged']);
        }

        $this->postJson('/api/v1/auth/apple', ['identityToken' => 'good-apple'])->assertTooManyRequests();
    }
}
