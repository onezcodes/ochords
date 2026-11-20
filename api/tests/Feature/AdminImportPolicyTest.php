<?php

namespace Tests\Feature;

use App\Enums\AdminRole;
use App\Filament\Resources\Songs\SongResource;
use App\Models\User;
use App\Services\CatalogOwnerService;
use Database\Seeders\RolesAndPermissionsSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminImportPolicyTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolesAndPermissionsSeeder::class);
    }

    public function test_moderator_can_create_sheets_support_cannot(): void
    {
        $moderator = User::factory()->create(['is_admin' => true]);
        $moderator->assignRole(AdminRole::Moderator->value);
        $support = User::factory()->create(['is_admin' => true]);
        $support->assignRole(AdminRole::Support->value);

        $this->actingAs($moderator);
        $this->assertTrue(SongResource::canCreate());

        $this->actingAs($support);
        $this->assertFalse(SongResource::canCreate());
    }

    public function test_catalog_owner_is_reusable_and_not_staff(): void
    {
        $a = app(CatalogOwnerService::class)->owner();
        $b = app(CatalogOwnerService::class)->owner();

        $this->assertTrue($a->is($b));
        $this->assertFalse($a->is_admin);
        $this->assertSame('ochords', $a->handle);
        $this->assertSame('O Chords', $a->name);
    }
}
