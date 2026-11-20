<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;
use Spatie\Permission\Models\Permission;

class PermissionPolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::PermissionsViewAny);
    }

    public function view(User $user, Permission $permission): bool
    {
        return $this->allows($user, AdminPermission::PermissionsView);
    }

    public function create(User $user): bool
    {
        return false;
    }

    public function update(User $user, Permission $permission): bool
    {
        return false;
    }

    public function delete(User $user, Permission $permission): bool
    {
        return false;
    }
}
