<?php

namespace Database\Seeders;

use App\Models\LearnFret;
use App\Models\LearnPosition;
use App\Models\LessonQuestion;
use Illuminate\Database\Seeder;

/**
 * The guitar-neck learning path shown on the Learn tab. Safe to re-run.
 */
class LearnPathSeeder extends Seeder
{
    public function run(): void
    {
        $positions = [
            ['p1', 1, 'First chords', 'Mastered', []],
            ['p2', 2, 'Switch chords without stopping', 'Frets 1–8', [
                ['f1', 'Anchor fingers', 'lesson'],
                ['f2', 'C ↔ Am', 'lesson'],
                ['f3', 'C ↔ G/B · walk the bass', 'lesson'],
                ['f4', 'Am ↔ F', 'lesson'],
                ['f5', 'Encore bonus', 'encore'],
                ['f6', 'Strum it: Folk pattern', 'lesson'],
                ['f7', 'The round chord', 'lesson'],
                ['f8', 'Position 2 soundcheck', 'soundcheck'],
            ]],
            ['p3', 3, 'Your first song — စဉ်းစားပါ', 'Locked', []],
        ];

        foreach ($positions as [$id, $number, $title, $subtitle, $frets]) {
            LearnPosition::query()->updateOrCreate(['id' => $id], compact('number', 'title', 'subtitle'));
            foreach ($frets as $sort => [$fretId, $fretTitle, $kind]) {
                LearnFret::query()->updateOrCreate(
                    ['id' => $fretId],
                    ['learn_position_id' => $id, 'sort' => $sort, 'title' => $fretTitle, 'kind' => $kind],
                );
            }
        }

        $this->seedQuestions('f3');
    }

    private function seedQuestions(string $fretId): void
    {
        $questions = [
            ['q1', 'pickChord', 'Which chord walks the bass down from C?', 'C → ? → Am', ['G/B', 'G', 'Em', 'F'], 'G/B'],
            ['q2', 'pickChord', 'Which finger stays put from C to G/B?', 'Think about the A string', ['None', 'Index', 'Middle', 'Ring'], 'None'],
            ['q3', 'buildBar', 'Build the intro bar', 'C · G/B · Am · F', ['C', 'G/B', 'Am', 'F', 'G'], 'C G/B Am F'],
            ['q4', 'listen', 'Play C, then G/B', 'We’ll listen for the bass note', [], ''],
            ['q5', 'pickChord', 'What comes after Am in the verse?', 'C → G/B → Am → ?', ['F', 'G', 'Dm', 'E'], 'F'],
        ];

        foreach ($questions as $sort => [$id, $kind, $prompt, $detail, $options, $answer]) {
            LessonQuestion::query()->updateOrCreate(
                ['id' => $id],
                ['learn_fret_id' => $fretId, 'sort' => $sort] + compact('kind', 'prompt', 'detail', 'options', 'answer'),
            );
        }
    }
}
