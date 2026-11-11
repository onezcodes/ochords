<?php

namespace App\Policies;

use App\Models\Setlist;
use App\Models\User;

/** Setlists are private: only the owner may change or delete one. */
class SetlistPolicy
{
    public function update(User $user, Setlist $setlist): bool
    {
        return $setlist->user_id === $user->id;
    }

    public function delete(User $user, Setlist $setlist): bool
    {
        return $setlist->user_id === $user->id;
    }
}
