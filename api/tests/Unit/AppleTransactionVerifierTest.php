<?php

namespace Tests\Unit;

use App\Services\StoreKit\AppleTransactionVerifier;
use App\Services\StoreKit\TransactionSignatureException;
use RuntimeException;
use Tests\TestCase;

/**
 * Runs the real chain and signature checks against a synthetic CA (tests/Fixtures/storekit): a test root,
 * an intermediate with Apple's WWDR marker OID and a leaf with the in-app purchase marker OID, signed ES256.
 * The fixtures hold public certificates and signed tokens only — the keys were discarded after signing.
 */
class AppleTransactionVerifierTest extends TestCase
{
    private const FIXTURES = __DIR__.'/../Fixtures/storekit';

    private function verifier(): AppleTransactionVerifier
    {
        return new class(self::FIXTURES.'/test-root.pem') extends AppleTransactionVerifier
        {
            public const ROOT_SHA256 = 'f3222cfddd60393a48863be9c35c0221d946624fded874f0c15b284665065151';
        };
    }

    public function test_a_transaction_signed_through_the_root_verifies_with_its_claims(): void
    {
        $transaction = $this->verifier()->verify(trim((string) file_get_contents(self::FIXTURES.'/transaction.jws')));

        $this->assertSame('com.onezcodes.ochords', $transaction->bundleId);
        $this->assertSame('com.onezcodes.ochords.pro.yearly', $transaction->productId);
        $this->assertSame('2000000123456789', $transaction->originalTransactionId);
        $this->assertSame('Sandbox', $transaction->environment);
        $this->assertSame('2031-06-01T00:00:00Z', $transaction->expiresDate?->toIso8601ZuluString());
        $this->assertNull($transaction->revocationDate);
    }

    public function test_a_chain_without_the_root_is_checked_against_the_bundled_root(): void
    {
        $transaction = $this->verifier()->verify(trim((string) file_get_contents(self::FIXTURES.'/transaction-short-chain.jws')));

        $this->assertSame('2000000987654321', $transaction->transactionId);
    }

    public function test_a_tampered_payload_fails_the_signature(): void
    {
        [$header, , $signature] = explode('.', trim((string) file_get_contents(self::FIXTURES.'/transaction.jws')));
        $payload = rtrim(strtr(base64_encode('{"bundleId":"com.onezcodes.ochords","productId":"com.onezcodes.ochords.pro.yearly","originalTransactionId":"1","transactionId":"1","environment":"Production","expiresDate":4102444800000}'), '+/', '-_'), '=');

        $this->expectException(TransactionSignatureException::class);
        $this->verifier()->verify("{$header}.{$payload}.{$signature}");
    }

    public function test_a_leaf_without_apples_in_app_purchase_marker_is_rejected(): void
    {
        $this->expectException(TransactionSignatureException::class);
        $this->verifier()->verify(trim((string) file_get_contents(self::FIXTURES.'/transaction-no-marker.jws')));
    }

    public function test_the_production_verifier_only_accepts_apples_root(): void
    {
        $this->expectException(RuntimeException::class);
        new AppleTransactionVerifier(self::FIXTURES.'/test-root.pem');
    }

    public function test_the_test_root_cannot_pass_for_apple(): void
    {
        // The production verifier (Apple's root) must reject the synthetic chain outright.
        $apple = new AppleTransactionVerifier(resource_path('certs/AppleRootCA-G3.pem'));
        $this->expectException(TransactionSignatureException::class);
        $apple->verify(trim((string) file_get_contents(self::FIXTURES.'/transaction.jws')));
    }
}
