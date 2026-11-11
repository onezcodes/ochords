<?php

namespace App\Services;

use App\Http\Resources\NotificationResource;
use App\Http\Resources\ProfileResource;
use App\Http\Resources\SetlistResource;
use App\Http\Resources\SongResource;
use App\Models\Feedback;
use App\Models\Report;
use App\Models\Song;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Everything the account holds about one player, decrypted, for a PDPO data-access request.
 * Other players appear only as the titles of public sheets this player saved.
 */
class ExportService
{
    public function __construct(private readonly ProfileService $profiles) {}

    /** @return array<string, mixed> */
    public function export(User $user, Request $request): array
    {
        // Every column, whatever the caller loaded.
        $user->refresh();
        $profile = (new ProfileResource($this->profiles->withWeek($user)))->toArray($request);
        $profile += [
            'createdAt' => $user->created_at?->toIso8601ZuluString(),
            'proUntil' => $user->pro_until?->toIso8601ZuluString(),
            'proOriginalTransactionID' => $user->pro_original_transaction_id,
            'appleID' => $user->apple_id,
            'googleID' => $user->google_id,
            'deletionRequestedAt' => $user->deletion_requested_at?->toIso8601ZuluString(),
            'deletionReason' => $user->deletion_reason,
            'followersCount' => $user->followers()->count(),
        ];

        $songs = Song::query()->withCommunityStats()->where('user_id', $user->id)->orderBy('added_at')->get();
        $export = [
            'exportedAt' => now()->toIso8601ZuluString(),
            'profile' => $profile,
            'songs' => $songs->map(fn (Song $song) => (new SongResource($song))->toArray($request))->all(),
            'savedSheets' => $user->savedSongs()->orderByPivot('created_at')->get()->map(fn (Song $song) => [
                'songID' => $song->id,
                'title' => $song->title,
                'rating' => $song->pivot?->getAttribute('rating'),
                'savedAt' => optional($song->pivot?->getAttribute('created_at'))->toIso8601ZuluString(),
            ])->all(),
            'setlists' => $user->setlists()->orderBy('created_at')->get()
                ->map(fn ($setlist) => (new SetlistResource($setlist))->toArray($request))->all(),
            'notifications' => $user->appNotifications()->orderBy('created_at')->get()
                ->map(fn ($notification) => (new NotificationResource($notification))->toArray($request))->all(),
            'learning' => [
                'fretProgress' => $user->completedFrets()->get()->map(fn ($fret) => [
                    'fretID' => $fret->id,
                    'stars' => (int) $fret->pivot?->getAttribute('stars'),
                    'completedAt' => optional($fret->pivot?->getAttribute('created_at'))->toIso8601ZuluString(),
                ])->all(),
                'practiceDays' => $user->practiceDays()->orderBy('day')->get()->map(fn ($day) => [
                    'day' => $day->day->toDateString(),
                    'minutes' => $day->minutes,
                ])->all(),
            ],
            'reports' => $user->reports()->orderBy('created_at')->get()->map(fn (Report $report) => [
                'songID' => $report->song_id,
                'reason' => $report->reason->value,
                'detail' => $report->detail,
                'createdAt' => $report->created_at->toIso8601ZuluString(),
            ])->all(),
            'feedback' => $user->feedback()->orderBy('created_at')->get()->map(fn (Feedback $feedback) => [
                'kind' => $feedback->kind->value,
                'message' => $feedback->message,
                'createdAt' => $feedback->created_at->toIso8601ZuluString(),
            ])->all(),
        ];
        Log::info('account.exported', ['user_id' => $user->id]);

        return $export;
    }
}
