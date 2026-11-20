<?php

namespace App\Policies;

use App\Enums\AdminPermission;
use App\Models\Song;
use App\Models\User;
use App\Policies\Concerns\ChecksAdminPermissions;
use Illuminate\Auth\Access\Response;

class SongPolicy
{
    use ChecksAdminPermissions;

    /** Staff list in Filament. */
    public function viewAny(User $user): bool
    {
        return $this->allows($user, AdminPermission::SongsViewAny);
    }

    /** Anyone can see public sheets; private sheets only their owner, savers, or staff. */
    public function view(?User $user, Song $song): bool
    {
        if ($user !== null && $this->allows($user, AdminPermission::SongsView)) {
            return true;
        }

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

    /** Staff create / import from scan in Filament. */
    public function create(User $user): bool
    {
        return $this->allows($user, AdminPermission::SongsCreate);
    }

    /** Staff metadata / section edits in Filament. */
    public function update(User $user, Song $song): bool
    {
        return $this->allows($user, AdminPermission::SongsUpdate);
    }

    /** Staff delete a sheet from Filament (e.g. a bad scan import). */
    public function delete(User $user, Song $song): bool
    {
        return $this->allows($user, AdminPermission::SongsDelete);
    }

    /** Delete your own sheet, or remove a saved sheet from your library. */
    public function remove(User $user, Song $song): bool
    {
        return $song->user_id === $user->id || $song->savers()->whereKey($user->id)->exists();
    }

    /** Hide / unhide from Explore (Filament / CLI). */
    public function moderate(User $user): bool
    {
        return $this->allows($user, AdminPermission::SongsModerate);
    }
}
