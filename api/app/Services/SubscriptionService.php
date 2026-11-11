<?php

namespace App\Services;

use App\Models\User;
use App\Services\StoreKit\MalformedTransactionException;
use App\Services\StoreKit\SignedTransaction;
use App\Services\StoreKit\TransactionVerifier;
use Illuminate\Support\Facades\Log;

/**
 * O Chords Pro: the app sends its current StoreKit 2 transaction and the account mirrors its state.
 * Pro lasts until `pro_until`; nothing here talks to the App Store Server API.
 */
class SubscriptionService
{
    private const SANDBOX_ENVIRONMENTS = ['Sandbox', 'Xcode'];

    public function __construct(private readonly TransactionVerifier $verifier) {}

    public function sync(User $user, string $transactionJws): User
    {
        $transaction = $this->verifier->verify($transactionJws);
        $this->checkTransaction($transaction);

        $until = $transaction->expiresDate;
        if ($transaction->revocationDate !== null && $transaction->revocationDate->lt($until)) {
            $until = $transaction->revocationDate;
        }
        $user->forceFill([
            'pro_until' => $until,
            'pro_original_transaction_id' => $transaction->originalTransactionId,
        ])->save();
        Log::info('pro.synced', [
            'user_id' => $user->id,
            'product_id' => $transaction->productId,
            'environment' => $transaction->environment,
            'pro_until' => $until->toIso8601ZuluString(),
        ]);

        return $user;
    }

    /** "Restore purchases found nothing": the account is no longer Pro. */
    public function clear(User $user): User
    {
        $user->forceFill(['pro_until' => null, 'pro_original_transaction_id' => null])->save();
        Log::info('pro.cleared', ['user_id' => $user->id]);

        return $user;
    }

    /**
     * @phpstan-assert !null $transaction->expiresDate
     */
    private function checkTransaction(SignedTransaction $transaction): void
    {
        if ($transaction->bundleId !== (string) config('services.apple.bundle_id')) {
            throw new MalformedTransactionException('This transaction is for another app.');
        }
        /** @var list<string> $products */
        $products = config('services.apple.pro_products', []);
        if (! in_array($transaction->productId, $products, true)) {
            throw new MalformedTransactionException('This product doesn’t include O Chords Pro.');
        }
        if ($transaction->expiresDate === null) {
            throw new MalformedTransactionException('This transaction isn’t a subscription.');
        }
        if (in_array($transaction->environment, self::SANDBOX_ENVIRONMENTS, true) && ! config('services.apple.allow_sandbox_receipts')) {
            Log::notice('pro.sandbox_rejected', ['environment' => $transaction->environment]);
            throw new MalformedTransactionException('Sandbox purchases aren’t accepted here.');
        }
    }
}
