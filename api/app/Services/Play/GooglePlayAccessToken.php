<?php

namespace App\Services\Play;

use Firebase\JWT\JWT;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use RuntimeException;

/**
 * Resolves a Play Developer API OAuth access token from a static token or a service-account JSON key.
 */
class GooglePlayAccessToken
{
    private const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';

    private const TOKEN_URL = 'https://oauth2.googleapis.com/token';

    public function resolve(): string
    {
        $static = trim((string) config('services.google_play.access_token'));
        if ($static !== '') {
            return $static;
        }

        $path = trim((string) config('services.google_play.service_account_json'));
        if ($path === '') {
            return '';
        }

        return Cache::remember('ochords:play:access_token', now()->addMinutes(45), function () use ($path): string {
            return $this->fetchFromServiceAccount($path);
        });
    }

    private function fetchFromServiceAccount(string $path): string
    {
        $absolute = str_starts_with($path, '/') ? $path : base_path($path);
        if (! is_file($absolute)) {
            throw new RuntimeException('OCHORDS_PLAY_SERVICE_ACCOUNT_JSON file not found.');
        }

        /** @var array{client_email?: string, private_key?: string} $key */
        $key = json_decode((string) file_get_contents($absolute), true, 512, JSON_THROW_ON_ERROR);
        $email = (string) ($key['client_email'] ?? '');
        $privateKey = (string) ($key['private_key'] ?? '');
        if ($email === '' || $privateKey === '') {
            throw new RuntimeException('Play service-account JSON is missing client_email or private_key.');
        }

        $now = time();
        $jwt = JWT::encode([
            'iss' => $email,
            'scope' => self::SCOPE,
            'aud' => self::TOKEN_URL,
            'iat' => $now,
            'exp' => $now + 3600,
        ], $privateKey, 'RS256');

        $response = Http::asForm()->timeout(15)->post(self::TOKEN_URL, [
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt,
        ]);

        if (! $response->successful()) {
            Log::notice('pro.play.token_refresh_failed', ['status' => $response->status()]);
            throw new RuntimeException('Could not refresh the Google Play access token.');
        }

        $token = (string) $response->json('access_token');
        if ($token === '') {
            throw new RuntimeException('Google Play token response had no access_token.');
        }

        return $token;
    }
}
