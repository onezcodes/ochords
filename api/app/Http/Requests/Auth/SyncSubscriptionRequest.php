<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class SyncSubscriptionRequest extends FormRequest
{
    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'platform' => ['sometimes', 'string', Rule::in(['ios', 'android'])],
            // StoreKit 2 (iOS)
            'transactionJWS' => [
                'required_without:purchaseToken',
                'nullable',
                'string',
                'max:16384',
                'regex:/^[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+$/',
            ],
            // Google Play Billing (Android)
            'purchaseToken' => ['required_without:transactionJWS', 'nullable', 'string', 'max:4096'],
            'productId' => ['required_with:purchaseToken', 'nullable', 'string', 'max:255'],
        ];
    }
}
