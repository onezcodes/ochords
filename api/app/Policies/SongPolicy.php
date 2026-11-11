<?php

namespace App\Policies;

use App\Models\Song;
use App\Models\User;
use Illuminate\Auth\Access\Response;

class SongPolicy
{
    /** Anyone can see public sheets; private sheets only their owner and players who saved them. */
    public function view(?User $user, Song $song): bool
    {
        if ($song->is_public) {
            return true;
        }

        return $user !== null && ($song->user_id === $user->id || $song->savers()->whereKey($user->id)->exists());
    }

    /** Update your own sheet, or add someone else's public sheet to your library. */
    public function save(User $user, Song $song): Response
    {
        return $song->user_id === $user->id || $song->is_public
            ? Response::allow()
            : Response::denyAsNotFound();
    }

    /** Delete your own sheet, or remove a saved sheet from your library. */
    public function remove(User $user, Song $song): bool
    {
        return $song->user_id === $user->id || $song->savers()->whereKey($user->id)->exists();
    }
}
