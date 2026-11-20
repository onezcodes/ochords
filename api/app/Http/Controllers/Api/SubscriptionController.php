<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Auth\SyncSubscriptionRequest;
use App\Http\Resources\ProfileResource;
use App\Models\User;
use App\Services\ProfileService;
use App\Services\SubscriptionService;
use Illuminate\Http\Request;

/** O Chords Pro: the app syncs StoreKit 2 or Play Billing; both calls answer with the Profile. */
class SubscriptionController extends Controller
{
    public function __construct(
        private readonly SubscriptionService $subscriptions,
        private readonly ProfileService $profiles,
    ) {}

    public function update(SyncSubscriptionRequest $request): ProfileResource
    {
        $account = $this->user($request);
        if ($request->filled('purchaseToken')) {
            $user = $this->subscriptions->syncPlay(
                $account,
                $request->string('purchaseToken')->toString(),
                $request->string('productId')->toString(),
            );
        } else {
            $user = $this->subscriptions->sync($account, $request->string('transactionJWS')->toString());
        }

        return new ProfileResource($this->profiles->withWeek($user));
    }

    /** "Restore purchases" found nothing: the account is no longer Pro. */
    public function destroy(Request $request): ProfileResource
    {
        $user = $this->subscriptions->clear($this->user($request));

        return new ProfileResource($this->profiles->withWeek($user));
    }

    private function user(Request $request): User
    {
        /** @var User */
        return $request->user();
    }
}
