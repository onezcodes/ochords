<?php

namespace App\Enums;

/**
 * Player tiers, earned with O Points. Raw values match the app's `Tier` enum.
 */
enum Tier: int
{
    case OpenString = 1;
    case Strummer = 2;
    case ChordKeeper = 3;
    case SessionPlayer = 4;
    case Maestro = 5;

    public function minPoints(): int
    {
        return match ($this) {
            self::OpenString => 0,
            self::Strummer => 300,
            self::ChordKeeper => 1000,
            self::SessionPlayer => 3000,
            self::Maestro => 8000,
        };
    }

    public static function forPoints(int $points): self
    {
        $tier = self::OpenString;
        foreach (self::cases() as $case) {
            if ($points >= $case->minPoints()) {
                $tier = $case;
            }
        }

        return $tier;
    }
}
