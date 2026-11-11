<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\LearnPathSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class LearnTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(LearnPathSeeder::class);
    }

    public function test_guests_start_at_the_first_fret(): void
    {
        $frets = collect($this->getJson('/api/v1/learn/path')->assertOk()->json())->flatMap(fn ($p) => $p['frets']);

        $this->assertSame('current', $frets->firstWhere('id', 'f1')['state']);
        $this->assertSame('locked', $frets->firstWhere('id', 'f2')['state']);
    }

    public function test_completed_frets_are_done_with_stars_and_the_next_is_current(): void
    {
        $user = User::factory()->create();
        $user->completedFrets()->attach(['f1' => ['stars' => 3], 'f2' => ['stars' => 2]]);

        $path = $this->actingAs($user)->getJson('/api/v1/learn/path')->assertOk()
            ->assertJsonStructure(['*' => ['id', 'number', 'title', 'subtitle', 'frets' => ['*' => ['id', 'title', 'state', 'kind', 'stars']]]]);
        $frets = collect($path->json())->flatMap(fn ($p) => $p['frets'])->keyBy('id');

        $this->assertSame(['done', 2], [$frets['f2']['state'], $frets['f2']['stars']]);
        $this->assertSame('current', $frets['f3']['state']);
        $this->assertSame('soundcheck', $frets['f8']['kind']);
    }

    public function test_lesson_questions_in_order(): void
    {
        $this->getJson('/api/v1/learn/frets/f3/questions')
            ->assertOk()
            ->assertJsonCount(5)
            ->assertJsonPath('0.id', 'q1')
            ->assertJsonPath('0.options', ['G/B', 'G', 'Em', 'F'])
            ->assertJsonPath('2.kind', 'buildBar');
    }

    public function test_unknown_fret_is_404(): void
    {
        $this->getJson('/api/v1/learn/frets/nope/questions')->assertNotFound();
    }
}
