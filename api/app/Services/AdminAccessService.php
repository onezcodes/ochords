<?php

namespace App\Services;

use App\Enums\AdminRole;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Validation\ValidationException;
use Spatie\Permission\Models\Role;
use Spatie\Permission\PermissionRegistrar;

/**
 * Staff access for Filament /admin: roles (Spatie) kept in sync with users.is_admin.
 */
class AdminAccessService
{
    public function __construct(private readonly AdminAuditService $audit) {}

    public function grant(User $user, AdminRole $role = AdminRole::SuperAdmin): void
    {
        DB::transaction(function () use ($user, $role): void {
            $user->forceFill(['is_admin' => true])->save();
            $user->assignRole($role->value);
        });
        app(PermissionRegistrar::class)->forgetCachedPermissions();
        Log::info('admin.granted', ['user_id' => $user->id, 'role' => $role->value]);
        $this->audit->log('admin.granted', $user, ['role' => $role->value]);
    }

    public function revoke(User $user): void
    {
        if ($this->remainingAdminsExcluding($user) === 0) {
            throw ValidationException::withMessages(['handle' => 'Cannot revoke the last admin.']);
        }

        DB::transaction(function () use ($user): void {
            $user->syncRoles([]);
            $user->forceFill(['is_admin' => false])->save();
        });
        app(PermissionRegistrar::class)->forgetCachedPermissions();
        Log::info('admin.revoked', ['user_id' => $user->id]);
        $this->audit->log('admin.revoked', $user);
    }

    /**
     * @param  list<string>  $roleNames
     */
    public function syncRoles(User $user, array $roleNames, ?User $actor = null): void
    {
        $actor ??= auth()->user() instanceof User ? auth()->user() : null;
        if ($actor && $actor->is($user) && $roleNames === []) {
            throw ValidationException::withMessages(['roles' => 'You cannot remove all of your own roles.']);
        }

        $willBeStaff = $roleNames !== [];
        if (! $willBeStaff && $this->remainingAdminsExcluding($user) === 0) {
            throw ValidationException::withMessages(['roles' => 'Cannot leave the system without any staff admin.']);
        }

        $user->syncRoles($roleNames);
        $this->syncAdminFlag($user);
        app(PermissionRegistrar::class)->forgetCachedPermissions();
        $this->audit->log('user.roles_updated', $user, ['roles' => $roleNames]);
    }

    /** Sync is_admin from whether the user still holds any panel role. */
    public function syncAdminFlag(User $user): void
    {
        $staff = $user->hasAnyRole(AdminRole::panelRoles());
        if ((bool) $user->is_admin !== $staff) {
            $user->forceFill(['is_admin' => $staff])->save();
        }
    }

    public function remainingAdminsExcluding(User $user): int
    {
        return User::query()
            ->where('is_admin', true)
            ->whereKeyNot($user->id)
            ->count();
    }

    /** @return list<string> */
    public function assignableRoleNames(): array
    {
        return Role::query()->where('guard_name', 'web')->orderBy('name')->pluck('name')->all();
    }
}
