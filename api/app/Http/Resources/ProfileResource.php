<?php

namespace App\Http\Resources;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `Profile`: the signed-in player's own account and progress.
 * Expects `week_minutes` (7 days, oldest first) to be set by ProfileService.
 *
 * @mixin User
 */
class ProfileResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        /** @var list<int> $week */
        $week = $this->resource->getAttribute('week_minutes') ?? array_fill(0, 7, 0);

        return [
            'user' => new UserSummaryResource($this->resource),
            'email' => $this->email,
            'points' => $this->points,
            'grooveDays' => $this->groove_days,
            'bestGroove' => $this->best_groove,
            'dailyGoalMinutes' => $this->daily_goal_minutes,
            'todayMinutes' => end($week) ?: 0,
            'week' => $week,
            'restNotes' => $this->rest_notes,
            'songsLearned' => $this->songs_learned,
            'lessonsDone' => $this->lessons_done,
            'isPro' => $this->resource->isPro(),
        ];
    }
}
