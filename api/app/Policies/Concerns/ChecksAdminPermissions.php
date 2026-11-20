<?php

namespace App\Policies\Concerns;

use App\Enums\AdminPermission;
use App\Models\User;

trait ChecksAdminPermissions
{
    protected function allows(User $user, AdminPermission $permission): bool
    {
        if ($user->isSuperAdmin()) {
            return true;
        }

        return $user->can($permission->value);
    }
}
