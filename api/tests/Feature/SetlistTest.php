<?php

namespace Tests\Feature;

use App\Models\Setlist;
use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class SetlistTest extends TestCase
{
    use RefreshDatabase;

    private const ID = 'f0e1d2c3-0000-4000-8000-000000000001';

    public function test_creating_a_setlist_returns_201_in_the_apps_shape(): void
    {
        $user = User::factory()->create();
        $own = Song::factory()->for($user, 'owner')->create();
        $saved = Song::factory()->public()->create();
        $saved->savers()->attach($user->id);

        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Sunday', 'note' => 'Church', 'songIDs' => [$saved->id, $own->id]])
            ->assertCreated()
            ->assertJsonPath('id', self::ID)
            ->assertJsonPath('name', 'Sunday')
            ->assertJsonPath('note', 'Church')
            ->assertJsonPath('songIDs', [$saved->id, $own->id])
            ->assertJsonPath('date', now()->format('j M Y'))
            ->assertJsonStructure(['id', 'name', 'note', 'songIDs', 'date']);

        $this->actingAs($user)->getJson('/api/v1/setlists')->assertJsonCount(1)->assertJsonPath('0.songIDs', [$saved->id, $own->id]);
    }

    public function test_updating_reorders_songs_and_returns_200(): void
    {
        $user = User::factory()->create();
        [$a, $b, $c] = Song::factory()->count(3)->for($user, 'owner')->create();
        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => [$a->id, $b->id, $c->id]])->assertCreated();

        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set 2', 'songIDs' => [$c->id, $a->id]])
            ->assertOk()
            ->assertJsonPath('name', 'Set 2')
            ->assertJsonPath('note', '')
            ->assertJsonPath('songIDs', [$c->id, $a->id]);

        $this->assertSame(1, Setlist::query()->count());
    }

    public function test_deleting_a_setlist(): void
    {
        $user = User::factory()->create();
        $song = Song::factory()->for($user, 'owner')->create();
        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => [$song->id]])->assertCreated();

        $this->actingAs($user)->deleteJson('/api/v1/setlists/'.self::ID)->assertNoContent();

        $this->assertSame(0, Setlist::query()->count());
        $this->assertSame(0, DB::table('setlist_song')->count());
        $this->assertModelExists($song);
    }

    public function test_another_players_setlist_is_forbidden(): void
    {
        $owner = User::factory()->create();
        $this->actingAs($owner)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Mine', 'songIDs' => []])->assertCreated();
        $other = User::factory()->create();

        $this->actingAs($other)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Taken', 'songIDs' => []])->assertForbidden();
        $this->actingAs($other)->deleteJson('/api/v1/setlists/'.self::ID)->assertForbidden();

        $this->assertSame('Mine', Setlist::query()->firstOrFail()->name);
    }

    public function test_a_song_outside_the_library_is_rejected(): void
    {
        $user = User::factory()->create();
        $own = Song::factory()->for($user, 'owner')->create();
        $notSaved = Song::factory()->public()->create();

        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => [$own->id, $notSaved->id]])
            ->assertUnprocessable()
            ->assertJsonPath('message', 'A song in this setlist isn’t in your library.')
            ->assertJsonValidationErrors('songIDs');
        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => ['no-such-song']])->assertUnprocessable();

        $this->assertSame(0, Setlist::query()->count());
    }

    public function test_invalid_setlists_are_rejected(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => str_repeat('x', 81), 'songIDs' => []])
            ->assertUnprocessable()->assertJsonValidationErrors('name');
        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set'])
            ->assertUnprocessable()->assertJsonValidationErrors('songIDs');
    }

    public function test_deleting_a_song_takes_it_out_of_setlists(): void
    {
        $user = User::factory()->create();
        [$keep, $gone] = Song::factory()->count(2)->for($user, 'owner')->create();
        $this->actingAs($user)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => [$keep->id, $gone->id]])->assertCreated();

        $this->actingAs($user)->deleteJson("/api/v1/songs/{$gone->id}")->assertNoContent();

        $this->actingAs($user)->getJson('/api/v1/setlists')->assertJsonPath('0.songIDs', [$keep->id]);
    }

    public function test_removing_a_saved_sheet_takes_it_out_of_your_setlists_only(): void
    {
        $sheet = Song::factory()->public()->create();
        $me = User::factory()->create();
        $sheet->savers()->attach($me->id);
        $this->actingAs($me)->putJson('/api/v1/setlists/'.self::ID, ['name' => 'Set', 'songIDs' => [$sheet->id]])->assertCreated();
        $this->actingAs($sheet->owner)->putJson('/api/v1/setlists/other', ['name' => 'Owner set', 'songIDs' => [$sheet->id]])->assertCreated();

        $this->actingAs($me)->deleteJson("/api/v1/songs/{$sheet->id}")->assertNoContent();

        $this->actingAs($me)->getJson('/api/v1/setlists')->assertJsonPath('0.songIDs', []);
        $this->actingAs($sheet->owner)->getJson('/api/v1/setlists')->assertJsonPath('0.songIDs', [$sheet->id]);
    }
}
