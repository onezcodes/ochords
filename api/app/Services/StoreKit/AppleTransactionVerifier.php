<?php

namespace App\Services\StoreKit;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Log;
use OpenSSLCertificate;
use RuntimeException;
use stdClass;
use Throwable;

/**
 * Offline verification of a StoreKit 2 transaction JWS, the way Apple's App Store Server Library does it:
 * the header's `x5c` chain must lead to Apple Root CA - G3 (bundled), the leaf must carry Apple's
 * in-app-purchase marker, and the leaf key must verify the ES256 signature. The App Store Server API is
 * not consulted, so a later refund or cancellation is only seen when the app syncs again.
 */
class AppleTransactionVerifier implements TransactionVerifier
{
    /**
     * SHA-256 fingerprint of resources/certs/AppleRootCA-G3.pem — AppleRootCA-G3.cer from
     * https://www.apple.com/certificateauthority/ converted with `openssl x509 -inform DER`.
     * Checked at start-up so a swapped file can't pass someone else's chain.
     */
    public const ROOT_SHA256 = '63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179';

    /** Marks a certificate Apple issues for signing in-app purchase receipts and transactions. */
    private const IN_APP_PURCHASE_OID = '1.2.840.113635.100.6.11.1';

    /** Marks the Apple Worldwide Developer Relations intermediate. */
    private const WWDR_INTERMEDIATE_OID = '1.2.840.113635.100.6.2.1';

    private const ALGORITHM = 'ES256';

    private const MAX_CHAIN_LENGTH = 3;

    private readonly OpenSSLCertificate $root;

    public function __construct(string $rootCertificatePath)
    {
        $pem = @file_get_contents($rootCertificatePath);
        $root = $pem === false ? false : $this->readCertificate($pem);
        if ($root === false || openssl_x509_fingerprint($root, 'sha256') !== static::ROOT_SHA256) {
            throw new RuntimeException('Apple Root CA - G3 is missing or is not the expected certificate.');
        }
        $this->root = $root;
    }

    public function verify(string $jws): SignedTransaction
    {
        [$header, $claims] = $this->segments($jws);
        $chain = $this->chain($header);
        $signedDate = $this->date($claims->signedDate ?? null);
        $this->verifyChain($chain, $signedDate ?? Carbon::now());

        $leafKey = openssl_pkey_get_public($chain[0]);
        if ($leafKey === false) {
            throw new TransactionSignatureException('This transaction isn’t signed by Apple.');
        }
        try {
            JWT::decode($jws, new Key($leafKey, self::ALGORITHM));
        } catch (Throwable $e) {
            Log::notice('pro.transaction_rejected', ['reason' => $e::class]);
            throw new TransactionSignatureException('This transaction isn’t signed by Apple.', 0, $e);
        }

        return new SignedTransaction(
            bundleId: $this->string($claims, 'bundleId'),
            productId: $this->string($claims, 'productId'),
            originalTransactionId: $this->string($claims, 'originalTransactionId'),
            transactionId: $this->string($claims, 'transactionId'),
            environment: $this->string($claims, 'environment'),
            expiresDate: $this->date($claims->expiresDate ?? null),
            revocationDate: $this->date($claims->revocationDate ?? null),
            signedDate: $signedDate,
        );
    }

    /** @return array{stdClass, stdClass} header and payload */
    private function segments(string $jws): array
    {
        $parts = explode('.', $jws);
        if (count($parts) !== 3) {
            throw new MalformedTransactionException('This isn’t a signed transaction.');
        }
        try {
            $header = JWT::jsonDecode(JWT::urlsafeB64Decode($parts[0]));
            $claims = JWT::jsonDecode(JWT::urlsafeB64Decode($parts[1]));
        } catch (Throwable $e) {
            throw new MalformedTransactionException('This isn’t a signed transaction.', 0, $e);
        }
        if (! $header instanceof stdClass || ! $claims instanceof stdClass) {
            throw new MalformedTransactionException('This isn’t a signed transaction.');
        }
        if (($header->alg ?? null) !== self::ALGORITHM) {
            throw new MalformedTransactionException('Unsupported transaction signature.');
        }

        return [$header, $claims];
    }

    /** @return list<OpenSSLCertificate> leaf first */
    private function chain(stdClass $header): array
    {
        $x5c = $header->x5c ?? null;
        if (! is_array($x5c) || $x5c === [] || count($x5c) > self::MAX_CHAIN_LENGTH) {
            throw new MalformedTransactionException('The transaction has no certificate chain.');
        }
        $chain = [];
        foreach ($x5c as $der) {
            $cert = is_string($der) ? $this->readCertificate("-----BEGIN CERTIFICATE-----\n".chunk_split($der, 64, "\n").'-----END CERTIFICATE-----') : false;
            if ($cert === false) {
                throw new MalformedTransactionException('The transaction’s certificate chain can’t be read.');
            }
            $chain[] = $cert;
        }

        return $chain;
    }

    /**
     * Each certificate must be issued by the next, the last by Apple's root (which may itself be included),
     * all valid at the signing date; the leaf and intermediate must carry Apple's in-app purchase markers.
     *
     * @param  list<OpenSSLCertificate>  $chain
     */
    private function verifyChain(array $chain, Carbon $at): void
    {
        $last = $chain[count($chain) - 1];
        if (openssl_x509_fingerprint($last, 'sha256') === static::ROOT_SHA256) {
            array_pop($chain);
        }
        if (count($chain) < 2) {
            $this->reject('chain_too_short');
        }

        foreach ($chain as $i => $cert) {
            $issuer = $chain[$i + 1] ?? $this->root;
            if (openssl_x509_verify($cert, $issuer) !== 1) {
                $this->reject('chain_link');
            }
            $parsed = openssl_x509_parse($cert);
            if ($parsed === false || $parsed['validFrom_time_t'] > $at->getTimestamp() || $parsed['validTo_time_t'] < $at->getTimestamp()) {
                $this->reject('certificate_validity');
            }
            $extensions = $parsed['extensions'] ?? [];
            $marker = $i === 0 ? self::IN_APP_PURCHASE_OID : self::WWDR_INTERMEDIATE_OID;
            if (! array_key_exists($marker, $extensions)) {
                $this->reject('certificate_purpose');
            }
        }
    }

    /** openssl warns on junk input; the caller treats false as a rejection, so the warning is noise. */
    private function readCertificate(string $pem): OpenSSLCertificate|false
    {
        return @openssl_x509_read($pem);
    }

    private function reject(string $reason): never
    {
        Log::notice('pro.transaction_rejected', ['reason' => $reason]);
        throw new TransactionSignatureException('This transaction isn’t signed by Apple.');
    }

    private function string(stdClass $claims, string $claim): string
    {
        $value = $claims->{$claim} ?? null;
        if (! is_scalar($value) || (string) $value === '') {
            throw new MalformedTransactionException("The transaction has no {$claim}.");
        }

        return (string) $value;
    }

    /** StoreKit dates are milliseconds since the epoch. */
    private function date(mixed $milliseconds): ?Carbon
    {
        return is_numeric($milliseconds) ? Carbon::createFromTimestampMs((int) $milliseconds, 'UTC') : null;
    }
}
