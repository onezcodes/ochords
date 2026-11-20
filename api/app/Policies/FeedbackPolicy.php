<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\Feedback;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;

class FeedbackPolicy
{
    use ChecksAdminPermissions;

    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::FeedbackViewAny);
    }

    public function view(User $user, Feedback $feedback): bool
    {
        return $this->allows($user, AdminPermission::FeedbackView);
    }

    public function review(User $user): bool
    {
        return $this->allows($user, AdminPermission::FeedbackReview);
    }
}
