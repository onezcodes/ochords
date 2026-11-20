<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\LearnPosition;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;

class LearnPositionPolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::LearnViewAny);
    }

    public function view(User $user, LearnPosition $learnPosition): bool
    {
        return $this->allows($user, AdminPermission::LearnView);
    }

    public function create(User $user): bool
    {
        return $this->allows($user, AdminPermission::LearnCreate);
    }

    public function update(User $user, LearnPosition $learnPosition): bool
    {
        return $this->allows($user, AdminPermission::LearnUpdate);
    }

    public function delete(User $user, LearnPosition $learnPosition): bool
    {
        return $this->allows($user, AdminPermission::LearnDelete);
    }
}
