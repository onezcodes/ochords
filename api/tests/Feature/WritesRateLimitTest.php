<?php

namespace Tests\Feature;

use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class WritesRateLimitTest extends TestCase
{
    use RefreshDatabase;

    public function test_feedback_is_rate_limited(): void
    {
        for ($i = 0; $i < 10; $i++) {
            $this->postJson('/api/v1/feedback', ['kind' => 'idea', 'message' => "Idea {$i}"])->assertCreated();
        }

        $this->postJson('/api/v1/feedback', ['kind' => 'idea', 'message' => 'One too many'])
            ->assertTooManyRequests();
    }

    public function test_reports_are_rate_limited(): void
    {
        $sheet = Song::factory()->public()->create();
        $user = User::factory()->create();

        for ($i = 0; $i < 10; $i++) {
            $this->actingAs($user)
                ->postJson("/api/v1/songs/{$sheet->id}/reports", ['reason' => 'spam'])
                ->assertCreated();
        }

        $this->actingAs($user)
            ->postJson("/api/v1/songs/{$sheet->id}/reports", ['reason' => 'spam'])
            ->assertTooManyRequests();
    }
}
