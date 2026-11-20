<?php

namespace App\Services\Play;

use App\Services\StoreKit\MalformedTransactionException;

class DisabledGooglePlayVerifier implements GooglePlayVerifier
{
    public function verify(string $purchaseToken, string $productId): array
    {
        throw new MalformedTransactionException('Google Play Pro verification is not configured on this server.');
    }

    public function isConfigured(): bool
    {
        return false;
    }
}
