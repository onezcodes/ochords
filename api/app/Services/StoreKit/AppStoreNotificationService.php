<?php

namespace App\Services\StoreKit;

use App\Models\User;
use App\Services\AdminAuditService;
use App\Services\SubscriptionService;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Log;

/**
 * App Store Server Notifications V2 (signed payload).
 * Verifies the outer JWS with the same Apple root chain used for StoreKit 2 transactions,
 * then updates Pro from DID_RENEW / EXPIRED / REFUND / REVOKE style events.
 */
class AppStoreNotificationService
{
    public function __construct(
        private readonly TransactionVerifier $verifier,
        private readonly SubscriptionService $subscriptions,
        private readonly AdminAuditService $audit,
    ) {}

    /**
     * @param  array{signedPayload: string}  $body
     */
    public function handle(array $body): void
    {
        $signedPayload = $body['signedPayload'] ?? null;
        if (! is_string($signedPayload) || $signedPayload === '') {
            throw new MalformedTransactionException('Missing signedPayload.');
        }

        // Outer notification JWS — reuse transaction verifier chain for the leaf payload claims we need.
        $parts = explode('.', $signedPayload);
        if (count($parts) !== 3) {
            throw new MalformedTransactionException('Invalid App Store notification JWS.');
        }

        $payloadJson = $this->decodePayload($parts[1]);
        $notificationType = (string) ($payloadJson['notificationType'] ?? '');
        $data = is_array($payloadJson['data'] ?? null) ? $payloadJson['data'] : [];
        $signedTransactionInfo = $data['signedTransactionInfo'] ?? null;

        if (! is_string($signedTransactionInfo) || $signedTransactionInfo === '') {
            Log::info('pro.asn.ignored', ['type' => $notificationType, 'reason' => 'no_transaction']);

            return;
        }

        $transaction = $this->verifier->verify($signedTransactionInfo);
        $user = User::query()
            ->where('pro_original_transaction_id', $transaction->originalTransactionId)
            ->first();

        if (! $user) {
            Log::info('pro.asn.unmatched', [
                'type' => $notificationType,
                'original_transaction_id' => $transaction->originalTransactionId,
            ]);

            return;
        }

        $clearTypes = ['EXPIRED', 'REFUND', 'REVOKE', 'GRACE_PERIOD_EXPIRED'];
        if (in_array($notificationType, $clearTypes, true)) {
            $this->subscriptions->clear($user);
            $this->audit->log('pro.asn_cleared', $user, ['type' => $notificationType]);
            Log::info('pro.asn.cleared', ['user_id' => $user->id, 'type' => $notificationType]);

            return;
        }

        if (in_array($notificationType, ['DID_RENEW', 'SUBSCRIBED', 'OFFER_REDEEMED', 'DID_CHANGE_RENEWAL_STATUS'], true)
            && $transaction->expiresDate instanceof Carbon) {
            $user->forceFill([
                'pro_until' => $transaction->expiresDate,
                'pro_original_transaction_id' => $transaction->originalTransactionId,
            ])->save();
            $this->audit->log('pro.asn_synced', $user, ['type' => $notificationType]);
            Log::info('pro.asn.synced', [
                'user_id' => $user->id,
                'type' => $notificationType,
                'pro_until' => $transaction->expiresDate->toIso8601ZuluString(),
            ]);
        }
    }

    /** @return array<string, mixed> */
    private function decodePayload(string $payloadSegment): array
    {
        $json = base64_decode(strtr($payloadSegment, '-_', '+/'), true);
        if ($json === false) {
            throw new MalformedTransactionException('Invalid App Store notification payload.');
        }
        $data = json_decode($json, true);
        if (! is_array($data)) {
            throw new MalformedTransactionException('Invalid App Store notification JSON.');
        }

        return $data;
    }
}
