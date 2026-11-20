<?php

namespace Database\Seeders;

use App\Enums\AdminRole;
use App\Models\User;
use App\Services\AdminAccessService;
use Illuminate\Database\Seeder;

/**
 * Non-production staff account for /admin. Never runs in production.
 */
class StaffSeeder extends Seeder
{
    public function run(): void
    {
        if (app()->isProduction()) {
            return;
        }

        $this->call(RolesAndPermissionsSeeder::class);

        $handle = (string) config('ochords.admin.seed_handle');
        $email = (string) config('ochords.admin.seed_email');
        $password = (string) config('ochords.admin.seed_password');

        $user = User::query()->where('handle', $handle)->first();
        if (! $user) {
            $user = User::create([
                'name' => 'O Chords Admin',
                'handle' => $handle,
                'email' => $email,
                'password' => $password,
            ]);
        } else {
            $user->forceFill(['password' => $password])->save();
        }

        app(AdminAccessService::class)->grant($user, AdminRole::SuperAdmin);
        $this->command?->info("Staff ready: handle={$handle} email={$email}");
    }
}
