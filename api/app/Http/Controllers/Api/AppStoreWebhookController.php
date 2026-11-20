<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\StoreKit\AppStoreNotificationService;
use App\Services\StoreKit\MalformedTransactionException;
use App\Services\StoreKit\TransactionSignatureException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * App Store Server Notifications V2.
 * Configure the production/sandbox URL in App Store Connect to POST here.
 */
class AppStoreWebhookController extends Controller
{
    public function __invoke(Request $request, AppStoreNotificationService $notifications): JsonResponse
    {
        try {
            $notifications->handle($request->all());
        } catch (TransactionSignatureException|MalformedTransactionException $e) {
            Log::notice('pro.asn.rejected', ['message' => $e->getMessage()]);

            return response()->json(['message' => $e->getMessage()], 401);
        }

        return response()->json(['ok' => true]);
    }
}
