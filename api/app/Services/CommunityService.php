<?php

namespace App\Services;

use App\Enums\FeedbackKind;
use App\Enums\ReportReason;
use App\Models\AppNotification;
use App\Models\Feedback;
use App\Models\Report;
use App\Models\Setlist;
use App\Models\Song;
use App\Models\User;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Validation\ValidationException;

/**
 * Setlists, notifications, player profiles, reports and feedback.
 */
class CommunityService
{
    private const NOTIFICATION_LIMIT = 100;

    /** @return Collection<int, Setlist> */
    public function setlists(User $user): Collection
    {
        return $user->setlists()->orderBy('created_at')->get();
    }

    /**
     * PUT semantics keyed by the app's setlist ID. Every song must be in the player's library
     * (their own, or a public sheet they saved). The caller checks SetlistPolicy on an existing one.
     *
     * @param  array{name: string, note?: ?string, songIDs: list<string>}  $data
     */
    public function saveSetlist(User $user, ?Setlist $existing, string $id, array $data): Setlist
    {
        $songIds = array_values(array_unique($data['songIDs']));
        if (! $this->allInLibrary($user, $songIds)) {
            throw ValidationException::withMessages(['songIDs' => 'A song in this setlist isn’t in your library.']);
        }

        $setlist = $existing ?? new Setlist(['id' => $id]);
        $setlist->fill([
            'name' => $data['name'],
            'note' => $data['note'] ?? '',
            // Shown on the setlist card; the last time it changed.
            'date' => now()->format('j M Y'),
        ]);
        $setlist->user_id = $user->id;

        DB::transaction(function () use ($setlist, $songIds) {
            $setlist->save();
            $setlist->syncSongIds($songIds);
        });
        Log::info('setlist.saved', ['setlist_id' => $setlist->id, 'user_id' => $user->id, 'songs' => count($songIds)]);

        return $setlist;
    }

    public function deleteSetlist(Setlist $setlist): void
    {
        $setlist->delete();
        Log::info('setlist.deleted', ['setlist_id' => $setlist->id, 'user_id' => $setlist->user_id]);
    }

    /** @param list<string> $songIds */
    private function allInLibrary(User $user, array $songIds): bool
    {
        if ($songIds === []) {
            return true;
        }
        $visible = Song::query()
            ->whereIn('id', $songIds)
            ->where(fn (Builder $q) => $q
                ->where('user_id', $user->id)
                ->orWhereHas('savers', fn (Builder $s) => $s->where('users.id', $user->id)))
            ->count();

        return $visible === count($songIds);
    }

    /** @return Collection<int, AppNotification> */
    public function notifications(User $user): Collection
    {
        return $user->appNotifications()->latest()->limit(self::NOTIFICATION_LIMIT)->get();
    }

    public function markAllRead(User $user): void
    {
        $user->appNotifications()->whereNull('read_at')->update(['read_at' => now()]);
    }

    public function player(User $user): User
    {
        return $user->loadCount([
            'followers',
            'songs as public_songs_count' => fn ($q) => $q->where('is_public', true),
        ]);
    }

    public function report(User $reporter, Song $song, ReportReason $reason, ?string $detail): Report
    {
        $report = new Report(['song_id' => $song->id, 'reason' => $reason, 'detail' => $detail]);
        $report->user_id = $reporter->id;
        $report->save();
        Log::info('community.report', ['report_id' => $report->id, 'song_id' => $song->id, 'reason' => $reason->value]);

        return $report;
    }

    public function feedback(?User $user, FeedbackKind $kind, string $message): Feedback
    {
        $feedback = Feedback::create(['user_id' => $user?->id, 'kind' => $kind, 'message' => $message]);
        Log::info('community.feedback', ['feedback_id' => $feedback->id, 'kind' => $kind->value]);

        return $feedback;
    }

    /**
     * Retention: resolved reports and feedback hold free text, so they go once they are old enough.
     * Open reports stay until a moderator resolves them.
     *
     * @return array{reports: int, feedback: int}
     */
    public function purgeOldRecords(): array
    {
        $reports = Report::query()
            ->whereNotNull('resolved_at')
            ->where('resolved_at', '<=', now()->subDays((int) config('ochords.report_retention_days')))
            ->delete();
        $feedback = Feedback::query()
            ->where('created_at', '<=', now()->subDays((int) config('ochords.feedback_retention_days')))
            ->delete();
        Log::info('community.old_records_purged', ['reports' => $reports, 'feedback' => $feedback]);

        return ['reports' => $reports, 'feedback' => $feedback];
    }
}
