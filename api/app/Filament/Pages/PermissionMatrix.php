<?php

namespace App\Filament\Pages;

use App\Enums\AdminPermission;
use App\Enums\AdminRole;
use App\Models\User;
use BackedEnum;
use Filament\Notifications\Notification;
use Filament\Pages\Page;
use Filament\Support\Icons\Heroicon;
use Illuminate\Support\Facades\DB;
use Spatie\Permission\Models\Permission;
use Spatie\Permission\Models\Role;
use Spatie\Permission\PermissionRegistrar;
use UnitEnum;

class PermissionMatrix extends Page
{
    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedTableCells;

    protected static string|UnitEnum|null $navigationGroup = 'Access';

    protected static ?int $navigationSort = 2;

    protected static ?string $navigationLabel = 'Permission matrix';

    protected static ?string $title = 'Permission matrix';

    protected string $view = 'filament.pages.permission-matrix';

    /** @var array<string, array<string, bool>> role => permission => checked */
    public array $matrix = [];

    public static function canAccess(): bool
    {
        $user = auth()->user();

        return $user instanceof User && ($user->isSuperAdmin() || $user->can(AdminPermission::RolesUpdate->value));
    }

    public function mount(): void
    {
        $roles = Role::query()->where('guard_name', 'web')->orderBy('name')->get();
        $permissions = Permission::query()->where('guard_name', 'web')->orderBy('name')->pluck('name');

        foreach ($roles as $role) {
            $owned = $role->permissions->pluck('name')->all();
            foreach ($permissions as $permission) {
                $this->matrix[$role->name][$permission] = in_array($permission, $owned, true);
            }
        }
    }

    public function save(): void
    {
        if (! static::canAccess()) {
            abort(403);
        }

        DB::transaction(function (): void {
            foreach ($this->matrix as $roleName => $perms) {
                if ($roleName === AdminRole::SuperAdmin->value) {
                    // Keep super_admin fully granted.
                    Role::findByName($roleName, 'web')->syncPermissions(AdminPermission::values());

                    continue;
                }
                $granted = array_keys(array_filter($perms));
                Role::findByName($roleName, 'web')->syncPermissions($granted);
            }
        });

        app(PermissionRegistrar::class)->forgetCachedPermissions();
        Notification::make()->title('Permissions saved')->success()->send();
        $this->mount();
    }

    /** @return list<string> */
    public function permissionNames(): array
    {
        return AdminPermission::values();
    }

    /** @return list<string> */
    public function roleNames(): array
    {
        return array_keys($this->matrix);
    }
}
