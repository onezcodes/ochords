<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;

class GoogleSignInRequest extends FormRequest
{
    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'idToken' => ['required', 'string', 'max:8192'],
            'device_name' => ['nullable', 'string', 'max:100'],
        ];
    }
}
