<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class PlaySubscriptionTest extends TestCase
{
    use RefreshDatabase;

    private const PRODUCT = 'com.onezcodes.ochords.pro.monthly';

    private const PACKAGE = 'com.onezcodes.ochords';

    private function configurePlay(): void
    {
        config([
            'services.google_play.package_name' => self::PACKAGE,
            'services.google_play.access_token' => 'ya29.test-token',
            'services.google_play.service_account_json' => '',
            'services.google_play.pro_products' => [
                'com.onezcodes.ochords.pro.monthly',
                'com.onezcodes.ochords.pro.yearly',
            ],
        ]);
    }

    public function test_android_sync_makes_the_player_pro(): void
    {
        $this->configurePlay();
        // 2030-01-01T00:00:00Z
        Http::fake([
            'androidpublisher.googleapis.com/*' => Http::response([
                'expiryTimeMillis' => '1893456000000',
                'orderId' => 'GPA.1234-5678',
            ]),
        ]);

        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', [
            'platform' => 'android',
            'purchaseToken' => 'play-token-abc',
            'productId' => self::PRODUCT,
        ])
            ->assertOk()
            ->assertJsonPath('isPro', true)
            ->assertJsonStructure(['user', 'email', 'points', 'week', 'isPro']);

        $fresh = $user->fresh();
        $this->assertSame('2030-01-01T00:00:00Z', $fresh?->pro_until?->toIso8601ZuluString());
        $this->assertSame('GPA.1234-5678', $fresh?->pro_original_transaction_id);

        Http::assertSent(function ($request) {
            return str_contains($request->url(), '/applications/'.self::PACKAGE.'/purchases/subscriptions/'.self::PRODUCT.'/tokens/play-token-abc')
                && $request->hasHeader('Authorization', 'Bearer ya29.test-token');
        });
    }

    public function test_android_sync_fails_when_play_returns_4xx(): void
    {
        $this->configurePlay();
        Http::fake([
            'androidpublisher.googleapis.com/*' => Http::response(['error' => ['message' => 'Purchase token not found']], 404),
        ]);

        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', [
            'platform' => 'android',
            'purchaseToken' => 'bad-token',
            'productId' => self::PRODUCT,
        ])
            ->assertUnprocessable()
            ->assertJsonPath('message', 'Google Play could not verify this purchase.');

        $this->assertNull($user->fresh()?->pro_until);
    }

    public function test_android_sync_rejects_a_non_pro_product(): void
    {
        $this->configurePlay();
        Http::fake([
            'androidpublisher.googleapis.com/*' => Http::response([
                'expiryTimeMillis' => '1893456000000',
                'orderId' => 'GPA.stickers',
            ]),
        ]);

        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', [
            'platform' => 'android',
            'purchaseToken' => 'play-token-stickers',
            'productId' => 'com.onezcodes.ochords.stickers',
        ])
            ->assertUnprocessable()
            ->assertJsonPath('message', 'This product doesn’t include O Chords Pro.');

        $this->assertNull($user->fresh()?->pro_until);
    }
}
