<?php

namespace Tests\Unit;

use App\Services\Play\GooglePlayAccessToken;
use Firebase\JWT\JWT;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class GooglePlayAccessTokenTest extends TestCase
{
    public function test_prefers_static_access_token(): void
    {
        config([
            'services.google_play.access_token' => 'static-token',
            'services.google_play.service_account_json' => '',
        ]);

        $this->assertSame('static-token', app(GooglePlayAccessToken::class)->resolve());
    }

    public function test_refreshes_from_service_account_json(): void
    {
        $keyPath = storage_path('framework/testing/play-sa.json');
        @mkdir(dirname($keyPath), 0777, true);

        $private = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        $this->assertNotFalse($private);
        openssl_pkey_export($private, $pem);
        file_put_contents($keyPath, json_encode([
            'client_email' => 'play@ochords.iam.gserviceaccount.com',
            'private_key' => $pem,
        ]));

        config([
            'services.google_play.access_token' => '',
            'services.google_play.service_account_json' => $keyPath,
        ]);
        Cache::forget('ochords:play:access_token');

        Http::fake([
            'https://oauth2.googleapis.com/token' => Http::response(['access_token' => 'ya29.refreshed', 'expires_in' => 3600]),
        ]);

        $token = app(GooglePlayAccessToken::class)->resolve();
        $this->assertSame('ya29.refreshed', $token);

        Http::assertSent(function ($request) {
            if ($request->url() !== 'https://oauth2.googleapis.com/token') {
                return false;
            }
            $assertion = $request['assertion'] ?? '';
            $parts = explode('.', (string) $assertion);

            return count($parts) === 3 && JWT::urlsafeB64Decode($parts[0]) !== '';
        });

        @unlink($keyPath);
    }
}
