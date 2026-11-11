<?php

namespace App\Services;

use App\Models\LearnFret;
use App\Models\LearnPosition;
use App\Models\LessonQuestion;
use App\Models\User;
use Illuminate\Database\Eloquent\Collection;

class LearnService
{
    /**
     * The path with each fret's state for this player: done (completed), current (the first
     * one not done) or locked. Guests see the first fret as current and the rest locked.
     *
     * @return Collection<int, LearnPosition>
     */
    public function path(?User $user): Collection
    {
        $positions = LearnPosition::query()->with('frets')->orderBy('number')->get();
        $stars = $user ? $user->completedFrets()->pluck('fret_progress.stars', 'learn_frets.id') : collect();

        $currentFound = false;
        foreach ($positions as $position) {
            foreach ($position->frets as $fret) {
                if ($stars->has($fret->id)) {
                    $fret->setAttribute('progress_state', 'done');
                    $fret->setAttribute('progress_stars', $stars[$fret->id]);

                    continue;
                }
                $fret->setAttribute('progress_state', $currentFound ? 'locked' : 'current');
                $fret->setAttribute('progress_stars', 0);
                $currentFound = true;
            }
        }

        return $positions;
    }

    /** @return Collection<int, LessonQuestion> */
    public function questions(LearnFret $fret): Collection
    {
        return $fret->questions;
    }
}
