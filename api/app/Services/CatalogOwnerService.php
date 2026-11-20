<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

/**
 * House account that owns staff-imported catalog sheets so Explore shows "O Chords", not a person.
 */
class CatalogOwnerService
{
    public function owner(): User
    {
        $handle = (string) config('ochords.admin.catalog_handle', 'ochords');
        $email = (string) config('ochords.admin.catalog_email', 'catalog@ochords.local');
        $name = (string) config('ochords.admin.catalog_name', 'O Chords');

        $user = User::query()->where('handle', $handle)->first();
        if ($user) {
            return $user;
        }

        return User::query()->create([
            'name' => $name,
            'handle' => $handle,
            'email' => $email,
            // Random unusable password — catalog account is not for sign-in.
            'password' => Hash::make(Str::password(32)),
        ]);
    }
}
