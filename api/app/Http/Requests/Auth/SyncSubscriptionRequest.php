<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;

class SyncSubscriptionRequest extends FormRequest
{
    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            // StoreKit 2 `Transaction.jwsRepresentation`: three base64url segments, a few KB with its x5c chain.
            'transactionJWS' => ['required', 'string', 'max:16384', 'regex:/^[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+$/'],
        ];
    }
}
