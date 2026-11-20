<?php

namespace Tests\Feature;

use App\Enums\AdminPermission;
use App\Enums\AdminRole;
use App\Models\User;
use Database\Seeders\RolesAndPermissionsSeeder;
use Filament\Facades\Filament;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Spatie\Permission\Models\Permission;
use Spatie\Permission\Models\Role;
use Tests\TestCase;

class RolesAndPermissionsTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolesAndPermissionsSeeder::class);
    }

    public function test_seeder_creates_builtin_roles_and_permissions(): void
    {
        foreach (AdminRole::values() as $role) {
            $this->assertTrue(Role::query()->where('name', $role)->exists(), $role);
        }
        foreach (AdminPermission::values() as $permission) {
            $this->assertTrue(Permission::query()->where('name', $permission)->exists(), $permission);
        }
    }

    public function test_moderator_cannot_manage_roles(): void
    {
        $user = User::factory()->create(['is_admin' => true]);
        $user->assignRole(AdminRole::Moderator->value);

        $this->assertTrue($user->can(AdminPermission::ReportsResolve->value));
        $this->assertFalse($user->can(AdminPermission::UsersManageRoles->value));
        $this->assertFalse($user->can(AdminPermission::RolesCreate->value));
        $this->assertTrue($user->canAccessPanel(Filament::getPanel('admin')));
    }

    public function test_support_can_manage_pro_but_not_reports(): void
    {
        $user = User::factory()->create(['is_admin' => true]);
        $user->assignRole(AdminRole::Support->value);

        $this->assertTrue($user->can(AdminPermission::UsersManagePro->value));
        $this->assertFalse($user->can(AdminPermission::ReportsResolve->value));
    }

    public function test_grant_assigns_role(): void
    {
        $user = User::factory()->create(['handle' => 'newbie', 'is_admin' => false]);

        $this->artisan('ochords:admin:grant', ['handle' => 'newbie', '--role' => 'moderator'])->assertSuccessful();

        $user->refresh();
        $this->assertTrue($user->is_admin);
        $this->assertTrue($user->hasRole(AdminRole::Moderator->value));
    }

    public function test_profile_does_not_expose_roles(): void
    {
        $user = User::factory()->admin()->create();
        $token = $user->createToken('test')->plainTextToken;

        $this->withToken($token)
            ->getJson('/api/v1/me')
            ->assertOk()
            ->assertJsonMissingPath('roles')
            ->assertJsonMissingPath('permissions')
            ->assertJsonMissingPath('is_admin');
    }
}
