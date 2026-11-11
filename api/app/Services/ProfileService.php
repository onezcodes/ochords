<?php

namespace App\Services;

use App\Models\User;

class ProfileService
{
    private const WEEK_DAYS = 7;

    /** Adds `week_minutes`: minutes played on each of the last 7 days, oldest first (today last). */
    public function withWeek(User $user): User
    {
        $from = today()->subDays(self::WEEK_DAYS - 1);
        $played = $user->practiceDays()
            ->where('day', '>=', $from->toDateString())
            ->get()
            ->mapWithKeys(fn ($day) => [$day->day->toDateString() => $day->minutes]);

        $week = [];
        for ($i = 0; $i < self::WEEK_DAYS; $i++) {
            $week[] = (int) ($played[$from->copy()->addDays($i)->toDateString()] ?? 0);
        }
        $user->setAttribute('week_minutes', $week);

        return $user;
    }
}
