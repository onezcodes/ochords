<?php

namespace App\Services\StoreKit;

/**
 * Verifies a StoreKit 2 signed transaction (JWS) came from Apple and returns its claims.
 */
interface TransactionVerifier
{
    /**
     * @throws MalformedTransactionException
     * @throws TransactionSignatureException
     */
    public function verify(string $jws): SignedTransaction;
}
