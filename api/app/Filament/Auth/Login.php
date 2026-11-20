<?php

namespace App\Filament\Auth;

use App\Models\User;
use Filament\Auth\Pages\Login as BaseLogin;
use SensitiveParameter;

/**
 * Staff sign-in looks up accounts by email_hash (emails are encrypted at rest).
 */
class Login extends BaseLogin
{
    /**
     * @param  array<string, mixed>  $data
     * @return array<string, mixed>
     */
    protected function getCredentialsFromFormData(#[SensitiveParameter] array $data): array
    {
        return [
            'email_hash' => User::hashEmail((string) $data['email']),
            'password' => $data['password'],
        ];
    }
}
