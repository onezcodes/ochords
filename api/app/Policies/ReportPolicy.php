<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\Report;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;

class ReportPolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::ReportsViewAny);
    }

    public function view(User $user, Report $report): bool
    {
        return $this->allows($user, AdminPermission::ReportsView);
    }

    public function resolve(User $user): bool
    {
        return $this->allows($user, AdminPermission::ReportsResolve);
    }
}
