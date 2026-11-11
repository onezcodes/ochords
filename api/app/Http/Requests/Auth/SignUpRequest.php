<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rules\Password;

class SignUpRequest extends FormRequest
{
    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'name' => ['required', 'string', 'max:50'],
            'email' => ['required', 'string', 'email:rfc', 'max:254'],
            // Matches the hints on the app's sign-up screen.
            'password' => ['required', 'string', Password::min(8)->mixedCase()->numbers()],
            'device_name' => ['nullable', 'string', 'max:100'],
        ];
    }
}
