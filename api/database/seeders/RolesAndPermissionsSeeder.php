<?php

namespace Database\Seeders;

use App\Enums\AdminPermission;
use App\Enums\AdminRole;
use Illuminate\Database\Seeder;
use Spatie\Permission\Models\Permission;
use Spatie\Permission\Models\Role;
use Spatie\Permission\PermissionRegistrar;

/**
 * Seeds staff roles/permissions for /admin. Safe to re-run (upsert by name).
 * Assigns every permission to super_admin and admin; narrower sets for moderator/support.
 */
class RolesAndPermissionsSeeder extends Seeder
{
    public function run(): void
    {
        app(PermissionRegistrar::class)->forgetCachedPermissions();

        foreach (AdminPermission::values() as $name) {
            Permission::findOrCreate($name, 'web');
        }

        $all = Permission::query()->where('guard_name', 'web')->pluck('name')->all();

        Role::findOrCreate(AdminRole::SuperAdmin->value, 'web')->syncPermissions($all);
        Role::findOrCreate(AdminRole::Admin->value, 'web')->syncPermissions($all);

        Role::findOrCreate(AdminRole::Moderator->value, 'web')->syncPermissions([
            AdminPermission::ReportsViewAny->value,
            AdminPermission::ReportsView->value,
            AdminPermission::ReportsResolve->value,
            AdminPermission::FeedbackViewAny->value,
            AdminPermission::FeedbackView->value,
            AdminPermission::FeedbackReview->value,
            AdminPermission::SongsViewAny->value,
            AdminPermission::SongsView->value,
            AdminPermission::SongsCreate->value,
            AdminPermission::SongsUpdate->value,
            AdminPermission::SongsDelete->value,
            AdminPermission::SongsModerate->value,
            AdminPermission::UsersViewAny->value,
            AdminPermission::UsersView->value,
            AdminPermission::LearnViewAny->value,
            AdminPermission::LearnView->value,
        ]);

        Role::findOrCreate(AdminRole::Support->value, 'web')->syncPermissions([
            AdminPermission::FeedbackViewAny->value,
            AdminPermission::FeedbackView->value,
            AdminPermission::FeedbackReview->value,
            AdminPermission::SongsViewAny->value,
            AdminPermission::SongsView->value,
            AdminPermission::UsersViewAny->value,
            AdminPermission::UsersView->value,
            AdminPermission::UsersUpdate->value,
            AdminPermission::UsersManagePro->value,
        ]);
    }
}
