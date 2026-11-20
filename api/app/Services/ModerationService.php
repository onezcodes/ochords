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
 * Moderation for artisan commands and the Filament /admin panel. Hiding a sheet takes it off Explore by
 * making it private; the owner keeps it, and everyone involved hears about it through in-app notifications.
 */
class ModerationService
{
    public function __construct(private readonly AdminAuditService $audit) {}

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
    public function resolve(Report $report, ReportResolution $resolution, ?string $note = null): void
    {
        if ($resolution === ReportResolution::Hidden) {
            $song = $report->song;
            if ($song) {
                $this->hideSheet($song, $note);

                return;
            }
        }
        $this->close($report, $resolution);
        $this->audit->log('report.resolved', $report, [
            'resolution' => $resolution->value,
            'song_id' => $report->song_id,
        ]);
    }

    /** Takes a sheet off Explore (private), notifies its owner and anyone who reported it. */
    public function hideSheet(Song $song, ?string $note = null): void
    {
        $openReports = Report::query()->open()->where('song_id', $song->id)->get();

        DB::transaction(function () use ($song, $openReports, $note) {
            $attrs = [];
            if ($song->is_public) {
                $attrs['is_public'] = false;
            }
            if ($note !== null && $note !== '') {
                $attrs['moderation_note'] = $note;
            }
            if ($attrs !== []) {
                $song->forceFill($attrs)->save();
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
        $this->audit->log('sheet.hidden', $song, ['open_reports' => $openReports->count()]);
    }

    /** Puts a private sheet back on Explore and notifies its owner. */
    public function unhideSheet(Song $song, ?string $note = null): void
    {
        if ($song->is_public) {
            return;
        }

        $attrs = ['is_public' => true];
        if ($note !== null && $note !== '') {
            $attrs['moderation_note'] = $note;
        }
        $song->forceFill($attrs)->save();
        $this->notify($song->user_id, 'Sheet restored:', "“{$song->title}” is back on Explore.", 'eye');
        Log::info('moderation.sheet_unhidden', ['song_id' => $song->id]);
        $this->audit->log('sheet.unhidden', $song);
    }

    public function featureSheet(Song $song): void
    {
        if ($song->featured_at !== null) {
            return;
        }
        $song->forceFill(['featured_at' => now(), 'is_public' => true])->save();
        $this->audit->log('sheet.featured', $song);
    }

    public function unfeatureSheet(Song $song): void
    {
        if ($song->featured_at === null) {
            return;
        }
        $song->forceFill(['featured_at' => null])->save();
        $this->audit->log('sheet.unfeatured', $song);
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
