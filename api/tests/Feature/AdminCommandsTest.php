<?php

namespace Tests\Feature;

use App\Enums\AdminRole;
use App\Models\User;
use Database\Seeders\RolesAndPermissionsSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class AdminCommandsTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolesAndPermissionsSeeder::class);
    }

    public function test_grant_and_revoke_admin_by_handle(): void
    {
        $first = User::factory()->admin()->create(['handle' => 'staff1']);
        $second = User::factory()->create(['handle' => 'player1', 'is_admin' => false]);

        $this->artisan('ochords:admin:grant', ['handle' => 'player1', '--role' => 'admin'])->assertSuccessful();
        $this->assertTrue($second->fresh()?->is_admin);
        $this->assertTrue($second->fresh()?->hasRole(AdminRole::Admin->value));

        $this->artisan('ochords:admin:revoke', ['handle' => 'player1'])->assertSuccessful();
        $this->assertFalse($second->fresh()?->is_admin);
        $this->assertFalse($second->fresh()?->hasRole(AdminRole::Admin->value));

        $this->artisan('ochords:admin:revoke', ['handle' => 'staff1'])->assertFailed();
        $this->assertTrue($first->fresh()?->is_admin);
    }

    public function test_grant_unknown_handle_fails(): void
    {
        $this->artisan('ochords:admin:grant', ['handle' => 'missing'])->assertFailed();
    }

    public function test_set_password_updates_hash(): void
    {
        $user = User::factory()->create(['handle' => 'staff2']);

        $this->artisan('ochords:admin:set-password', [
            'handle' => 'staff2',
            'password' => 'NewPass99',
        ])->assertSuccessful();

        $this->assertTrue(Hash::check('NewPass99', $user->fresh()?->password));
    }
}
