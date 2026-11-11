<?php

namespace Database\Factories;

use App\Models\Song;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Song>
 */
class SongFactory extends Factory
{
    /** @return array<string, mixed> */
    public function definition(): array
    {
        return [
            'id' => (string) Str::uuid(),
            'user_id' => User::factory(),
            'title' => fake()->sentence(3),
            'artist' => fake()->name(),
            'key' => 'C',
            'tempo' => 80,
            'sections' => [[
                'id' => 'verse-1', 'kind' => 'verse', 'label' => 'VERSE 1', 'note' => null,
                'lines' => ['[C]Synthetic [G]lyric line'], 'bars' => [], 'flow' => [],
            ]],
            'source' => 'imported',
            'is_myanmar' => false,
            'is_public' => false,
            'added_at' => now(),
        ];
    }

    public function public(): static
    {
        return $this->state(['is_public' => true]);
    }
}
