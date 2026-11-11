<?php

namespace App\Services\StoreKit;

use Illuminate\Support\Carbon;

/**
 * The claims of a verified StoreKit 2 transaction (JWSTransactionDecodedPayload) that Pro depends on.
 */
final readonly class SignedTransaction
{
    public function __construct(
        public string $bundleId,
        public string $productId,
        public string $originalTransactionId,
        public string $transactionId,
        public string $environment,
        public ?Carbon $expiresDate,
        public ?Carbon $revocationDate,
        public ?Carbon $signedDate,
    ) {}
}
