<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;

class UserPolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::UsersViewAny);
    }

    public function view(User $user, User $model): bool
    {
        return $this->allows($user, AdminPermission::UsersView);
    }

    public function update(User $user, User $model): bool
    {
        return $this->allows($user, AdminPermission::UsersUpdate);
    }

    public function manageRoles(User $user): bool
    {
        return $this->allows($user, AdminPermission::UsersManageRoles);
    }

    public function managePro(User $user): bool
    {
        return $this->allows($user, AdminPermission::UsersManagePro);
    }
}
