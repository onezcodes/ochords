<?php

namespace Tests\Feature;

use App\Enums\NotificationCategory;
use App\Models\AppNotification;
use App\Models\Feedback;
use App\Models\Report;
use App\Models\Setlist;
use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class CommunityTest extends TestCase
{
    use RefreshDatabase;

    public function test_explore_lists_public_sheets_only_and_works_for_guests(): void
    {
        $public = Song::factory()->public()->create(['title' => 'စဉ်းစားပါ', 'difficulty' => 'Beginner']);
        Song::factory()->create(['title' => 'Private draft']);
        $public->savers()->attach(User::factory()->count(3)->create()->pluck('id'));

        $this->getJson('/api/v1/explore')
            ->assertOk()
            ->assertJsonCount(1)
            ->assertJsonPath('0.song.id', $public->id)
            ->assertJsonPath('0.votes', 3)
            ->assertJsonPath('0.difficulty', 'Beginner')
            ->assertJsonStructure(['*' => ['song' => ['id', 'uploader' => ['id', 'name', 'tier']], 'votes', 'comments', 'difficulty']]);
    }

    public function test_explore_searches_title_artist_and_uploader(): void
    {
        $uploader = User::factory()->create(['name' => 'Thiri']);
        Song::factory()->public()->for($uploader, 'owner')->create(['title' => 'One', 'artist' => 'X']);
        Song::factory()->public()->create(['title' => 'စဉ်းစားပါ', 'artist' => 'မေ']);
        Song::factory()->public()->create(['title' => 'Two', 'artist' => 'Lay Phyu']);

        $this->getJson('/api/v1/explore?'.http_build_query(['q' => 'စဉ်း']))->assertJsonCount(1)->assertJsonPath('0.song.title', 'စဉ်းစားပါ');
        $this->getJson('/api/v1/explore?q=lay')->assertJsonCount(1)->assertJsonPath('0.song.title', 'Two');
        $this->getJson('/api/v1/explore?q=thiri')->assertJsonCount(1)->assertJsonPath('0.song.title', 'One');
        // Wildcards are searched for literally.
        $this->getJson('/api/v1/explore?q=%25')->assertJsonCount(0);
        $this->getJson('/api/v1/explore?q=_')->assertJsonCount(0);
    }

    public function test_popular_orders_by_plays(): void
    {
        Song::factory()->public()->create(['id' => 'quiet', 'plays' => 5]);
        Song::factory()->public()->create(['id' => 'hit', 'plays' => 900]);

        $this->getJson('/api/v1/explore/popular')->assertJsonPath('0.song.id', 'hit')->assertJsonPath('0.song.plays', 900);
    }

    public function test_setlists_keep_their_song_order(): void
    {
        $user = User::factory()->create();
        foreach (['a', 'b', 'c'] as $id) {
            Song::factory()->for($user, 'owner')->create(['id' => $id]);
        }
        $setlist = new Setlist(['id' => 's1', 'name' => 'Sunday', 'note' => 'Sunday set', 'date' => null]);
        $setlist->user_id = $user->id;
        $setlist->save();
        $setlist->syncSongIds(['b', 'a', 'c']);

        $this->actingAs($user)->getJson('/api/v1/setlists')
            ->assertOk()
            ->assertExactJson([['id' => 's1', 'name' => 'Sunday', 'note' => 'Sunday set', 'songIDs' => ['b', 'a', 'c'], 'date' => null]]);
    }

    public function test_notifications_newest_first_and_mark_all_read(): void
    {
        $user = User::factory()->create();
        $older = new AppNotification(['category' => NotificationCategory::Learning, 'title' => 'Old', 'body' => 'b', 'system_image' => 'bolt.fill']);
        $older->user_id = $user->id;
        $older->created_at = now()->subDay();
        $older->save();
        $newer = new AppNotification(['category' => NotificationCategory::Community, 'title' => 'New', 'body' => 'b', 'system_image' => 'heart.fill']);
        $newer->user_id = $user->id;
        $newer->save();

        $this->actingAs($user)->getJson('/api/v1/notifications')
            ->assertJsonPath('0.title', 'New')
            ->assertJsonPath('0.unread', true)
            ->assertJsonPath('0.category', 'community')
            ->assertJsonPath('1.time', '1 day ago');

        $this->actingAs($user)->postJson('/api/v1/notifications/read-all')->assertNoContent();
        $this->actingAs($user)->getJson('/api/v1/notifications')->assertJsonPath('0.unread', false);
    }

    public function test_notifications_are_private(): void
    {
        $owner = User::factory()->create();
        $note = new AppNotification(['category' => NotificationCategory::System, 'title' => 'Mine', 'body' => 'b', 'system_image' => 'checkmark']);
        $note->user_id = $owner->id;
        $note->save();

        $this->actingAs(User::factory()->create())->getJson('/api/v1/notifications')->assertJsonCount(0);
    }

    public function test_player_profile_counts_public_sheets_and_followers(): void
    {
        $player = User::factory()->create(['city' => 'Yangon']);
        Song::factory()->public()->count(2)->for($player, 'owner')->create();
        Song::factory()->for($player, 'owner')->create();
        $player->followers()->attach(User::factory()->count(4)->create()->pluck('id'));

        $this->getJson("/api/v1/users/{$player->id}")
            ->assertOk()
            ->assertJson(['id' => (string) $player->id, 'sheets' => 2, 'followers' => 4, 'city' => 'Yangon', 'tier' => 1]);
    }

    public function test_reporting_a_sheet_stores_the_detail_encrypted(): void
    {
        $sheet = Song::factory()->public()->create();

        $this->actingAs(User::factory()->create())
            ->postJson("/api/v1/songs/{$sheet->id}/reports", ['reason' => 'wrong_chords', 'detail' => 'Verse 2 bar 3'])
            ->assertCreated()->assertJsonPath('status', 'received');

        $this->assertSame('Verse 2 bar 3', Report::query()->firstOrFail()->detail);
        $this->assertStringNotContainsString('Verse 2', (string) DB::table('reports')->value('detail'));
    }

    public function test_cannot_report_a_private_sheet_you_cannot_see(): void
    {
        $sheet = Song::factory()->create();

        $this->actingAs(User::factory()->create())
            ->postJson("/api/v1/songs/{$sheet->id}/reports", ['reason' => 'spam'])->assertForbidden();
    }

    public function test_feedback_works_for_guests_and_rejects_unknown_kinds(): void
    {
        $this->postJson('/api/v1/feedback', ['kind' => 'idea', 'message' => 'Dark stage mode please'])->assertCreated();
        $this->postJson('/api/v1/feedback', ['kind' => 'rant', 'message' => 'Hello there'])
            ->assertUnprocessable()->assertJsonValidationErrors('kind');

        $this->assertNull(Feedback::query()->firstOrFail()->user_id);
    }
}
