<?php

namespace App\Services\Play;

use Illuminate\Support\Carbon;

/**
 * Verifies a Google Play Billing purchase and returns entitlement expiry.
 * Bound to DisabledGooglePlayVerifier until Play Developer API credentials are configured.
 */
interface GooglePlayVerifier
{
    /**
     * @return array{productId: string, expiry: Carbon, orderId: string}
     */
    public function verify(string $purchaseToken, string $productId): array;

    public function isConfigured(): bool;
}
