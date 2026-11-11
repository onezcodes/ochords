<?php

namespace Tests\Feature;

use App\Enums\NotificationCategory;
use App\Models\AppNotification;
use App\Models\Feedback;
use App\Models\PracticeDay;
use App\Models\Report;
use App\Models\Setlist;
use App\Models\Song;
use App\Models\User;
use Database\Seeders\LearnPathSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ExportTest extends TestCase
{
    use RefreshDatabase;

    public function test_export_holds_everything_about_the_player_decrypted(): void
    {
        $this->seed(LearnPathSeeder::class);
        $user = User::factory()->create(['email' => 'me@example.com', 'city' => 'Yangon']);
        $own = Song::factory()->for($user, 'owner')->create(['my_note' => 'Capo 2', 'is_public' => true]);
        $saved = Song::factory()->public()->create(['title' => 'Their sheet', 'my_note' => 'Their secret']);
        $saved->savers()->attach($user->id, ['rating' => 4]);
        $setlist = new Setlist(['id' => 'set-1', 'name' => 'Sunday', 'note' => '', 'date' => null]);
        $setlist->user_id = $user->id;
        $setlist->save();
        $setlist->syncSongIds([$saved->id, $own->id]);
        $note = new AppNotification(['category' => NotificationCategory::System, 'title' => 'Hi', 'body' => 'there', 'system_image' => 'bell']);
        $note->user_id = $user->id;
        $note->save();
        PracticeDay::create(['user_id' => $user->id, 'day' => today(), 'minutes' => 12]);
        $user->completedFrets()->attach(['f1' => ['stars' => 3]]);
        $report = new Report(['song_id' => $saved->id, 'reason' => 'wrong_chords', 'detail' => 'Bar 3 is wrong']);
        $report->user_id = $user->id;
        $report->save();
        Feedback::create(['user_id' => $user->id, 'kind' => 'idea', 'message' => 'Dark stage mode']);
        Feedback::create(['user_id' => User::factory()->create()->id, 'kind' => 'idea', 'message' => 'Not mine']);

        $json = $this->actingAs($user)->getJson('/api/v1/me/export')
            ->assertOk()
            ->assertJsonStructure([
                'exportedAt', 'profile' => ['user', 'email', 'isPro', 'createdAt', 'appleID', 'googleID'],
                'songs', 'savedSheets', 'setlists', 'notifications',
                'learning' => ['fretProgress', 'practiceDays'], 'reports', 'feedback',
            ])
            ->assertJsonPath('profile.email', 'me@example.com')
            ->assertJsonPath('profile.user.city', 'Yangon')
            ->assertJsonCount(1, 'songs')
            ->assertJsonPath('songs.0.myNote', 'Capo 2')
            ->assertJsonPath('songs.0.isPublic', true)
            ->assertJsonPath('savedSheets.0.title', 'Their sheet')
            ->assertJsonPath('savedSheets.0.rating', 4)
            ->assertJsonPath('setlists.0.songIDs', [$saved->id, $own->id])
            ->assertJsonPath('notifications.0.title', 'Hi')
            ->assertJsonPath('learning.fretProgress.0', ['fretID' => 'f1', 'stars' => 3, 'completedAt' => now()->toIso8601ZuluString()])
            ->assertJsonPath('learning.practiceDays.0.minutes', 12)
            ->assertJsonPath('reports.0', ['songID' => $saved->id, 'reason' => 'wrong_chords', 'detail' => 'Bar 3 is wrong', 'createdAt' => $report->created_at->toIso8601ZuluString()])
            ->assertJsonCount(1, 'feedback')
            ->assertJsonPath('feedback.0.message', 'Dark stage mode')
            ->json();

        // Nothing about the other player beyond the public sheet's title.
        $this->assertStringNotContainsString('Their secret', json_encode($json, JSON_THROW_ON_ERROR));
        $this->assertArrayNotHasKey('uploader', $json['savedSheets'][0]);
    }

    public function test_export_is_rate_limited(): void
    {
        $user = User::factory()->create();
        for ($i = 0; $i < 3; $i++) {
            $this->actingAs($user)->getJson('/api/v1/me/export')->assertOk();
        }

        $this->actingAs($user)->getJson('/api/v1/me/export')->assertTooManyRequests();
    }
}
