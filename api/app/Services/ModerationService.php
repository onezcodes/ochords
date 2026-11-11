<?php

namespace App\Services;

use App\Enums\NotificationCategory;
use App\Enums\ReportResolution;
use App\Models\AppNotification;
use App\Models\Report;
use App\Models\Song;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

/**
 * Moderation, driven from artisan only (no admin HTTP surface). Hiding a sheet takes it off Explore by
 * making it private; the owner keeps it, and everyone involved hears about it through in-app notifications.
 */
class ModerationService
{
    /**
     * Open reports, oldest first, each with how many open reports its song has (`song_reports_count`).
     *
     * @return Collection<int, Report>
     */
    public function openReports(): Collection
    {
        $perSong = Report::query()->open()
            ->select('song_id', DB::raw('count(*) as total'))
            ->groupBy('song_id')
            ->pluck('total', 'song_id');

        return Report::query()->open()->orderBy('created_at')->get()
            ->each(fn (Report $report) => $report->setAttribute('song_reports_count', (int) ($perSong[$report->song_id] ?? 1)));
    }

    /** Closes one report. Hiding also hides the song and closes its other open reports as hidden. */
    public function resolve(Report $report, ReportResolution $resolution): void
    {
        if ($resolution === ReportResolution::Hidden) {
            $song = $report->song;
            if ($song) {
                $this->hideSheet($song);

                return;
            }
        }
        $this->close($report, $resolution);
    }

    /** Takes a sheet off Explore (private), notifies its owner and anyone who reported it. */
    public function hideSheet(Song $song): void
    {
        $openReports = Report::query()->open()->where('song_id', $song->id)->get();

        DB::transaction(function () use ($song, $openReports) {
            if ($song->is_public) {
                $song->forceFill(['is_public' => false])->save();
            }
            foreach ($openReports as $report) {
                $this->close($report, ReportResolution::Hidden);
            }
        });

        $this->notify($song->user_id, 'Sheet hidden:', "“{$song->title}” was taken off Explore after a report. It’s still in your library.", 'eye.slash');
        foreach ($openReports->pluck('user_id')->unique() as $reporterId) {
            $this->notify((int) $reporterId, 'Report update:', 'the sheet you reported was hidden. Thanks for keeping O Chords tidy.', 'checkmark');
        }
        Log::info('moderation.sheet_hidden', ['song_id' => $song->id, 'reports' => $openReports->count()]);
    }

    private function close(Report $report, ReportResolution $resolution): void
    {
        $report->forceFill(['status' => 'resolved', 'resolved_at' => now(), 'resolution' => $resolution])->save();
        Log::info('moderation.report_resolved', ['report_id' => $report->id, 'song_id' => $report->song_id, 'resolution' => $resolution->value]);
    }

    private function notify(int $userId, string $title, string $body, string $systemImage): void
    {
        $notification = new AppNotification([
            'category' => NotificationCategory::System, 'title' => $title, 'body' => $body, 'system_image' => $systemImage,
        ]);
        $notification->user_id = $userId;
        $notification->save();
    }
}
