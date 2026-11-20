<?php

namespace Tests\Feature;

use App\Models\AdminAuditLog;
use App\Models\Song;
use App\Models\User;
use App\Services\ModerationService;
use Database\Seeders\RolesAndPermissionsSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminOpsTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolesAndPermissionsSeeder::class);
    }

    public function test_featured_sheets_sort_first_on_explore(): void
    {
        $plain = Song::factory()->public()->create(['title' => 'Plain', 'plays' => 100]);
        $featured = Song::factory()->public()->create(['title' => 'Featured', 'plays' => 1, 'featured_at' => now()]);

        $this->getJson('/api/v1/explore')
            ->assertOk()
            ->assertJsonPath('0.song.id', $featured->id)
            ->assertJsonPath('0.isFeatured', true)
            ->assertJsonPath('1.song.id', $plain->id)
            ->assertJsonPath('1.isFeatured', false);
    }

    public function test_hide_and_unhide_write_audit_and_note(): void
    {
        $actor = User::factory()->admin()->create();
        $this->actingAs($actor);

        $song = Song::factory()->public()->create();
        $moderation = app(ModerationService::class);
        $moderation->hideSheet($song, 'spam chords');
        $this->assertFalse($song->fresh()?->is_public);
        $this->assertSame('spam chords', $song->fresh()?->moderation_note);
        $this->assertSame(1, AdminAuditLog::query()->where('action', 'sheet.hidden')->count());

        $moderation->unhideSheet($song->fresh(), 'false positive');
        $this->assertTrue($song->fresh()?->is_public);
        $this->assertSame('false positive', $song->fresh()?->moderation_note);
        $this->assertSame(1, AdminAuditLog::query()->where('action', 'sheet.unhidden')->count());
    }

    public function test_android_sync_rejected_when_play_not_configured(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/me/subscription', [
            'platform' => 'android',
            'purchaseToken' => 'token-abc',
            'productId' => 'com.onezcodes.ochords.pro.monthly',
        ])->assertUnprocessable();
    }

    public function test_admin_ip_allowlist_blocks_when_configured(): void
    {
        config(['ochords.admin.allowed_ips' => ['203.0.113.10']]);
        $user = User::factory()->admin()->create();

        $this->actingAs($user)
            ->withServerVariables(['REMOTE_ADDR' => '198.51.100.1'])
            ->get('/admin')
            ->assertForbidden();
    }
}
