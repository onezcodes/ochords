<?php

namespace App\Console\Commands;

use App\Enums\AdminRole;
use App\Models\User;
use App\Services\AdminAccessService;
use Illuminate\Console\Command;

class GrantAdminCommand extends Command
{
    protected $signature = 'ochords:admin:grant
        {handle : Player handle to promote}
        {--role=super_admin : Staff role (super_admin, admin, moderator, support)}';

    protected $description = 'Grant Filament /admin access (assigns a staff role and sets is_admin)';

    public function handle(AdminAccessService $access): int
    {
        $handle = (string) $this->argument('handle');
        $roleName = (string) $this->option('role');
        $role = AdminRole::tryFrom($roleName);
        if (! $role) {
            $this->error('Unknown role. Use: '.implode(', ', AdminRole::values()));

            return self::FAILURE;
        }

        $user = User::query()->where('handle', $handle)->first();
        if (! $user) {
            $this->error("No user with handle “{$handle}”.");

            return self::FAILURE;
        }

        $access->grant($user, $role);
        $this->info("Granted {$role->value} to {$user->handle} (id {$user->id}).");

        return self::SUCCESS;
    }
}
