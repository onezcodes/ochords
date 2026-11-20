<?php

namespace Tests\Feature;

use App\Enums\ReportResolution;
use App\Models\AppNotification;
use App\Models\Report;
use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class ModerationTest extends TestCase
{
    use RefreshDatabase;

    public function test_reports_lists_open_reports_without_detail_unless_asked(): void
    {
        $song = Song::factory()->public()->create();
        $first = $this->report($song, 'Verse 2 is wrong');
        $this->report($song, 'dup');
        $resolved = $this->report(Song::factory()->public()->create(), 'done');
        $resolved->forceFill(['resolved_at' => now(), 'resolution' => ReportResolution::Fixed])->save();

        $this->assertSame(0, Artisan::call('ochords:reports'));
        $output = Artisan::output();
        $this->assertStringContainsString("| {$first->id} ", $output);
        $this->assertStringContainsString($song->id, $output);
        $this->assertStringContainsString('| 2 ', $output, 'count of open reports on the song');
        $this->assertStringNotContainsString('Verse 2 is wrong', $output);
        $this->assertStringNotContainsString("| {$resolved->id} ", $output);
        $this->assertStringNotContainsString($resolved->song_id, $output);

        Artisan::call('ochords:reports', ['--detail' => true]);
        $this->assertStringContainsString('Verse 2 is wrong', Artisan::output());
    }

    public function test_resolving_with_hide_makes_the_sheet_private_and_notifies_owner_and_reporters(): void
    {
        $song = Song::factory()->public()->create();
        $report = $this->report($song, 'offensive');
        $other = $this->report($song, 'same');
        $unrelated = $this->report(Song::factory()->public()->create(), 'x');

        $this->artisan("ochords:reports:resolve {$report->id} --hide")->expectsOutputToContain('hidden')->assertSuccessful();

        $this->assertFalse($song->fresh()?->is_public);
        $this->assertSame(ReportResolution::Hidden, $report->fresh()?->resolution);
        $this->assertSame(ReportResolution::Hidden, $other->fresh()?->resolution);
        $this->assertNull($unrelated->fresh()?->resolved_at);
        $this->assertSame(1, AppNotification::query()->where('user_id', $song->user_id)->where('title', 'Sheet hidden:')->count());
        $this->assertSame(1, AppNotification::query()->where('user_id', $report->user_id)->where('title', 'Report update:')->count());
        $this->assertSame(1, AppNotification::query()->where('user_id', $other->user_id)->count());
        $this->getJson('/api/v1/explore')->assertJsonCount(1);
    }

    public function test_dismiss_and_fixed_resolve_without_touching_the_sheet(): void
    {
        $song = Song::factory()->public()->create();
        $dismissed = $this->report($song, 'a');
        $fixed = $this->report($song, 'b');

        $this->artisan("ochords:reports:resolve {$dismissed->id} --dismiss")->assertSuccessful();
        $this->artisan("ochords:reports:resolve {$fixed->id}")->assertSuccessful();

        $this->assertTrue($song->fresh()?->is_public);
        $this->assertSame(ReportResolution::Dismissed, $dismissed->fresh()?->resolution);
        $this->assertSame(ReportResolution::Fixed, $fixed->fresh()?->resolution);
        $this->assertSame(0, AppNotification::query()->count());
        $this->artisan("ochords:reports:resolve {$fixed->id}")->assertFailed();
        $this->artisan("ochords:reports:resolve {$fixed->id} --hide --dismiss")->assertFailed();
    }

    public function test_sheets_hide_works_without_a_report(): void
    {
        $song = Song::factory()->public()->create();

        $this->artisan("ochords:sheets:hide {$song->id}")->assertSuccessful();
        $this->artisan('ochords:sheets:hide nope')->assertFailed();

        $this->assertFalse($song->fresh()?->is_public);
        $this->assertSame(1, AppNotification::query()->where('user_id', $song->user_id)->count());
    }

    public function test_sheets_unhide_restores_public_and_notifies_owner(): void
    {
        $song = Song::factory()->create(['is_public' => false]);

        $this->artisan("ochords:sheets:unhide {$song->id}")->assertSuccessful();
        $this->artisan('ochords:sheets:unhide nope')->assertFailed();

        $this->assertTrue($song->fresh()?->is_public);
        $this->assertSame(1, AppNotification::query()->where('user_id', $song->user_id)->where('title', 'Sheet restored:')->count());
    }

    public function test_unhide_is_noop_when_already_public(): void
    {
        $song = Song::factory()->public()->create();

        $this->artisan("ochords:sheets:unhide {$song->id}")->assertSuccessful();

        $this->assertTrue($song->fresh()?->is_public);
        $this->assertSame(0, AppNotification::query()->count());
    }

    private function report(Song $song, string $detail): Report
    {
        $report = new Report(['song_id' => $song->id, 'reason' => 'offensive', 'detail' => $detail]);
        $report->user_id = User::factory()->create()->id;
        $report->save();

        return $report;
    }
}
