<?php

namespace Tests\Feature;

use App\Enums\ReportResolution;
use App\Models\Feedback;
use App\Models\Report;
use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Schedule;
use Tests\TestCase;

class RetentionTest extends TestCase
{
    use RefreshDatabase;

    public function test_purge_removes_old_resolved_reports_and_old_feedback_only(): void
    {
        $song = Song::factory()->public()->create();
        $oldResolved = $this->report($song, resolvedDaysAgo: 181);
        $recentResolved = $this->report($song, resolvedDaysAgo: 10);
        $oldOpen = $this->report($song, createdDaysAgo: 400);
        $oldFeedback = Feedback::create(['user_id' => null, 'kind' => 'idea', 'message' => 'old']);
        $oldFeedback->forceFill(['created_at' => now()->subDays(366)])->save();
        $newFeedback = Feedback::create(['user_id' => null, 'kind' => 'idea', 'message' => 'new']);

        $this->artisan('ochords:purge-old-records')
            ->expectsOutputToContain('1 resolved report(s) and 1 feedback message(s) deleted.')
            ->assertSuccessful();

        $this->assertModelMissing($oldResolved);
        $this->assertModelExists($recentResolved);
        $this->assertModelExists($oldOpen);
        $this->assertModelMissing($oldFeedback);
        $this->assertModelExists($newFeedback);
    }

    public function test_purges_are_scheduled_daily(): void
    {
        $commands = collect(Schedule::events())->map(fn ($event) => $event->command)->implode("\n");

        $this->assertStringContainsString('ochords:purge-old-records', $commands);
        $this->assertStringContainsString('ochords:purge-deleted-accounts', $commands);
    }

    private function report(Song $song, ?int $resolvedDaysAgo = null, int $createdDaysAgo = 0): Report
    {
        $report = new Report(['song_id' => $song->id, 'reason' => 'spam', 'detail' => 'x']);
        $report->user_id = User::factory()->create()->id;
        $report->created_at = now()->subDays($createdDaysAgo);
        if ($resolvedDaysAgo !== null) {
            $report->forceFill(['status' => 'resolved', 'resolved_at' => now()->subDays($resolvedDaysAgo), 'resolution' => ReportResolution::Fixed]);
        }
        $report->save();

        return $report;
    }
}
