<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminAccessTest extends TestCase
{
    use RefreshDatabase;

    public function test_guest_is_redirected_to_admin_login(): void
    {
        $this->get('/admin')->assertRedirect(route('filament.admin.auth.login'));
    }

    public function test_non_admin_cannot_access_admin_panel(): void
    {
        $user = User::factory()->create(['is_admin' => false]);

        $this->actingAs($user)->get('/admin')->assertForbidden();
    }

    public function test_admin_can_access_admin_panel(): void
    {
        $user = User::factory()->admin()->create();

        $this->actingAs($user)->get('/admin')->assertOk();
    }

    public function test_profile_json_does_not_expose_is_admin(): void
    {
        $user = User::factory()->admin()->create();
        $token = $user->createToken('test')->plainTextToken;

        $this->withToken($token)
            ->getJson('/api/v1/me')
            ->assertOk()
            ->assertJsonMissingPath('is_admin')
            ->assertJsonMissingPath('data.is_admin')
            ->assertJsonPath('isPro', false);
    }

    public function test_api_unauthenticated_still_returns_json_401(): void
    {
        $this->getJson('/api/v1/me')->assertUnauthorized();
    }
}
