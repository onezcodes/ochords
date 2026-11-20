<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Enums\AdminRole;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;
use Spatie\Permission\Models\Role;

class RolePolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::RolesViewAny);
    }

    public function view(User $user, Role $role): bool
    {
        return $this->allows($user, AdminPermission::RolesView);
    }

    public function create(User $user): bool
    {
        return $this->allows($user, AdminPermission::RolesCreate);
    }

    public function update(User $user, Role $role): bool
    {
        return $this->allows($user, AdminPermission::RolesUpdate);
    }

    public function delete(User $user, Role $role): bool
    {
        if ($role->name === AdminRole::SuperAdmin->value) {
            return false;
        }

        return $this->allows($user, AdminPermission::RolesDelete);
    }
}
