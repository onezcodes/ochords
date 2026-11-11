<?php

namespace App\Services\Social;

use App\Enums\SocialProvider;
use Firebase\JWT\JWK;
use Firebase\JWT\JWT;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use stdClass;
use Throwable;

/**
 * Checks the token's signature against the provider's published keys (JWKS, cached for an hour),
 * then its issuer, audience (our client/bundle ID) and expiry. Tokens are never logged.
 */
class JwksSocialTokenVerifier implements SocialTokenVerifier
{
    private const KEYS_TTL_SECONDS = 3600;

    private const CLOCK_LEEWAY_SECONDS = 60;

    private const APPLE_ISSUER = 'https://appleid.apple.com';

    private const GOOGLE_ISSUERS = ['https://accounts.google.com', 'accounts.google.com'];

    public function verifyApple(string $identityToken): SocialIdentity
    {
        $claims = $this->decode($identityToken, (string) config('services.apple.jwks_url'), SocialProvider::Apple);
        $this->check(($claims->iss ?? null) === self::APPLE_ISSUER, SocialProvider::Apple, 'issuer');
        $this->check($this->audienceMatches($claims, (string) config('services.apple.bundle_id')), SocialProvider::Apple, 'audience');

        // Apple sends `email_verified` as a bool or the string "true"; relay addresses are always verified.
        return new SocialIdentity(
            SocialProvider::Apple,
            $this->subject($claims, SocialProvider::Apple),
            $this->email($claims),
            $this->flag($claims->email_verified ?? true),
        );
    }

    public function verifyGoogle(string $idToken): SocialIdentity
    {
        $clientId = (string) config('services.google.client_id');
        if ($clientId === '') {
            throw new SocialSignInUnavailableException('Google sign-in isn’t set up yet.');
        }

        $claims = $this->decode($idToken, (string) config('services.google.jwks_url'), SocialProvider::Google);
        $this->check(in_array($claims->iss ?? null, self::GOOGLE_ISSUERS, true), SocialProvider::Google, 'issuer');
        $this->check($this->audienceMatches($claims, $clientId), SocialProvider::Google, 'audience');
        $this->check($this->flag($claims->email_verified ?? false), SocialProvider::Google, 'email_unverified');

        return new SocialIdentity(SocialProvider::Google, $this->subject($claims, SocialProvider::Google), $this->email($claims), true);
    }

    private function decode(string $token, string $jwksUrl, SocialProvider $provider): stdClass
    {
        try {
            JWT::$leeway = self::CLOCK_LEEWAY_SECONDS;

            return JWT::decode($token, JWK::parseKeySet($this->keys($jwksUrl, $provider), 'RS256'));
        } catch (Throwable $e) {
            Log::notice('auth.social.token_rejected', ['provider' => $provider->value, 'reason' => $e::class]);
            throw new InvalidSocialTokenException('This '.ucfirst($provider->value).' sign-in could not be verified.', 0, $e);
        }
    }

    /** @return array<string, mixed> */
    private function keys(string $url, SocialProvider $provider): array
    {
        /** @var array<string, mixed> */
        return Cache::remember('social-jwks:'.$provider->value, self::KEYS_TTL_SECONDS, function () use ($url, $provider) {
            $keys = Http::timeout(10)->acceptJson()->get($url)->throw()->json();
            if (! is_array($keys) || empty($keys['keys'])) {
                Log::warning('auth.social.jwks_unavailable', ['provider' => $provider->value]);
                throw new InvalidSocialTokenException('Sign-in keys could not be fetched.');
            }

            return $keys;
        });
    }

    private function audienceMatches(stdClass $claims, string $expected): bool
    {
        $audience = $claims->aud ?? null;
        $audiences = is_array($audience) ? $audience : [$audience];

        return $expected !== '' && in_array($expected, $audiences, true);
    }

    private function subject(stdClass $claims, SocialProvider $provider): string
    {
        $subject = $claims->sub ?? null;
        $this->check(is_string($subject) && $subject !== '' && strlen($subject) <= 255, $provider, 'subject');

        return $subject;
    }

    private function email(stdClass $claims): ?string
    {
        $email = $claims->email ?? null;

        return is_string($email) && filter_var($email, FILTER_VALIDATE_EMAIL) ? $email : null;
    }

    private function flag(mixed $value): bool
    {
        return $value === true || $value === 'true' || $value === 1;
    }

    private function check(bool $ok, SocialProvider $provider, string $what): void
    {
        if (! $ok) {
            Log::notice('auth.social.token_rejected', ['provider' => $provider->value, 'reason' => $what]);
            throw new InvalidSocialTokenException('This '.ucfirst($provider->value).' sign-in could not be verified.');
        }
    }
}
