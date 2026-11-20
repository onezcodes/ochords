<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Services\AdminAccessService;
use Illuminate\Console\Command;
use Illuminate\Validation\ValidationException;

class RevokeAdminCommand extends Command
{
    protected $signature = 'ochords:admin:revoke {handle : Player handle to demote}';

    protected $description = 'Revoke Filament /admin access (clears staff roles and is_admin)';

    public function handle(AdminAccessService $access): int
    {
        $handle = (string) $this->argument('handle');
        $user = User::query()->where('handle', $handle)->first();
        if (! $user) {
            $this->error("No user with handle “{$handle}”.");

            return self::FAILURE;
        }
        if (! $user->is_admin && $user->roles->isEmpty()) {
            $this->info("{$user->handle} is not staff.");

            return self::SUCCESS;
        }

        try {
            $access->revoke($user);
        } catch (ValidationException $e) {
            $this->error($e->getMessage());

            return self::FAILURE;
        }

        $this->info("Revoked admin from {$user->handle} (id {$user->id}).");

        return self::SUCCESS;
    }
}
