<?php

namespace App\Services\Play;

use App\Services\StoreKit\MalformedTransactionException;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Google Play Developer API (androidpublisher v3) subscription purchase lookup.
 * Requires OCHORDS_PLAY_PACKAGE_NAME plus either OCHORDS_PLAY_ACCESS_TOKEN or
 * OCHORDS_PLAY_SERVICE_ACCOUNT_JSON (auto-refreshed via GooglePlayAccessToken).
 */
class HttpGooglePlayVerifier implements GooglePlayVerifier
{
    public function __construct(
        private readonly string $packageName,
        private readonly string $accessToken,
    ) {}

    public function isConfigured(): bool
    {
        return $this->packageName !== '' && $this->accessToken !== '';
    }

    public function verify(string $purchaseToken, string $productId): array
    {
        if (! $this->isConfigured()) {
            throw new MalformedTransactionException('Google Play Pro verification is not configured on this server.');
        }

        $url = sprintf(
            'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s',
            rawurlencode($this->packageName),
            rawurlencode($productId),
            rawurlencode($purchaseToken),
        );

        $response = Http::withToken($this->accessToken)->timeout(15)->get($url);
        if (! $response->successful()) {
            Log::notice('pro.play.verify_failed', ['status' => $response->status(), 'product_id' => $productId]);
            throw new MalformedTransactionException('Google Play could not verify this purchase.');
        }

        $expiryMs = (int) ($response->json('expiryTimeMillis') ?? 0);
        if ($expiryMs <= 0) {
            throw new MalformedTransactionException('This Play purchase isn’t a subscription.');
        }

        /** @var list<string> $products */
        $products = config('services.apple.pro_products', []);
        // Play product IDs may differ; also allow OCHORDS_PLAY_PRO_PRODUCTS.
        $playProducts = config('services.google_play.pro_products', $products);
        if (! in_array($productId, $playProducts, true)) {
            throw new MalformedTransactionException('This product doesn’t include O Chords Pro.');
        }

        return [
            'productId' => $productId,
            'expiry' => Carbon::createFromTimestampMs($expiryMs),
            'orderId' => (string) ($response->json('orderId') ?? $purchaseToken),
        ];
    }
}
