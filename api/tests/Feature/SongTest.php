<?php

namespace Tests\Feature;

use App\Models\Song;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class SongTest extends TestCase
{
    use RefreshDatabase;

    /** A song exactly as the app's JSONEncoder sends it. */
    private function appSong(array $overrides = []): array
    {
        return array_merge([
            'id' => 'a1b2c3d4-0000-4000-8000-000000000001',
            'title' => 'စဉ်းစားပါ',
            'artist' => 'မေ',
            'key' => 'C',
            'tempo' => 76,
            'sections' => [
                ['id' => 'S1', 'kind' => 'music', 'label' => 'INTRO', 'note' => '4 bars', 'lines' => [], 'bars' => ['C', 'G/B'], 'flow' => []],
                ['id' => 'S2', 'kind' => 'verse', 'label' => 'VERSE 1', 'lines' => ['[C]ငါ မကြားချင် [G/B]ခဲ့သော'], 'bars' => [], 'flow' => []],
            ],
            'source' => 'imported',
            'isMyanmar' => true,
            'isPublic' => false,
            'addedAt' => '2026-10-28T09:00:00Z',
            'myNote' => 'Capo 2',
        ], $overrides);
    }

    public function test_saving_a_new_song_returns_it_in_the_apps_shape(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/songs/a1b2c3d4-0000-4000-8000-000000000001', $this->appSong())
            ->assertOk()
            ->assertJsonPath('id', 'a1b2c3d4-0000-4000-8000-000000000001')
            ->assertJsonPath('sections.1.lines.0', '[C]ငါ မကြားချင် [G/B]ခဲ့သော')
            ->assertJsonPath('myNote', 'Capo 2')
            ->assertJsonPath('addedAt', '2026-10-28T09:00:00Z')
            ->assertJsonMissingPath('uploader');

        $this->actingAs($user)->getJson('/api/v1/songs')->assertOk()->assertJsonCount(1);
    }

    public function test_sections_get_every_key_the_app_needs_to_decode(): void
    {
        $user = User::factory()->create();
        $song = $this->appSong(['sections' => [['kind' => 'verse', 'label' => 'VERSE 1', 'lines' => ['[C]la']]]]);

        $section = $this->actingAs($user)->putJson('/api/v1/songs/s1', $song)->assertOk()->json('sections.0');

        $this->assertSame(['id', 'kind', 'label', 'note', 'strum', 'lines', 'bars', 'flow'], array_keys($section));
        $this->assertSame([], $section['bars']);
        $this->assertNull($section['strum']);
    }

    public function test_a_strumming_pattern_read_from_the_sheet_is_kept(): void
    {
        $user = User::factory()->create();
        $song = $this->appSong(['sections' => [['kind' => 'verse', 'label' => 'VERSE 1', 'strum' => '↓ ↓ ↓ ↑ ↓', 'lines' => ['[C]la']]]]);

        $this->actingAs($user)->putJson('/api/v1/songs/s1', $song)->assertOk()->assertJsonPath('sections.0.strum', '↓ ↓ ↓ ↑ ↓');
        $this->actingAs($user)->getJson('/api/v1/songs/s1')->assertOk()->assertJsonPath('sections.0.strum', '↓ ↓ ↓ ↑ ↓');
    }

    public function test_updating_keeps_the_added_date_and_the_public_flag_when_left_out(): void
    {
        $user = User::factory()->create();
        $this->actingAs($user)->putJson('/api/v1/songs/s1', $this->appSong(['isPublic' => true]))->assertOk();

        $update = $this->appSong(['title' => 'New title', 'addedAt' => '2030-01-01T00:00:00Z']);
        unset($update['isPublic']);
        $this->actingAs($user)->putJson('/api/v1/songs/s1', $update)
            ->assertOk()
            ->assertJsonPath('title', 'New title')
            ->assertJsonPath('isPublic', true)
            ->assertJsonPath('addedAt', '2026-10-28T09:00:00Z');
    }

    public function test_private_note_is_encrypted_and_hidden_from_other_players(): void
    {
        $owner = User::factory()->create();
        $other = User::factory()->create();
        $this->actingAs($owner)->putJson('/api/v1/songs/s1', $this->appSong(['isPublic' => true]))->assertOk();

        $this->assertStringNotContainsString('Capo 2', (string) DB::table('songs')->value('my_note'));
        $this->actingAs($other)->getJson('/api/v1/songs/s1')
            ->assertOk()
            ->assertJsonMissingPath('myNote')
            ->assertJsonMissingPath('isPublic')
            ->assertJsonPath('uploader.id', (string) $owner->id);
    }

    public function test_saving_someone_elses_public_sheet_adds_it_to_your_library_without_changing_it(): void
    {
        $sheet = Song::factory()->public()->create(['title' => 'Original']);
        $me = User::factory()->create();

        $this->actingAs($me)->putJson("/api/v1/songs/{$sheet->id}", $this->appSong(['title' => 'Hijacked']))
            ->assertOk()->assertJsonPath('title', 'Original');

        $this->assertSame('Original', $sheet->fresh()?->title);
        $this->actingAs($me)->getJson('/api/v1/songs')->assertJsonPath('0.id', $sheet->id);
    }

    public function test_someone_elses_private_sheet_cannot_be_saved_or_read(): void
    {
        $sheet = Song::factory()->create();
        $me = User::factory()->create();

        $this->actingAs($me)->putJson("/api/v1/songs/{$sheet->id}", $this->appSong())->assertNotFound();
        $this->actingAs($me)->getJson("/api/v1/songs/{$sheet->id}")->assertForbidden();
    }

    public function test_removing_a_saved_sheet_keeps_it_for_its_owner(): void
    {
        $sheet = Song::factory()->public()->create();
        $me = User::factory()->create();
        $sheet->savers()->attach($me->id);

        $this->actingAs($me)->deleteJson("/api/v1/songs/{$sheet->id}")->assertNoContent();

        $this->assertModelExists($sheet);
        $this->actingAs($me)->getJson('/api/v1/songs')->assertJsonCount(0);
    }

    public function test_deleting_your_own_sheet(): void
    {
        $sheet = Song::factory()->create();

        $this->actingAs($sheet->owner)->deleteJson("/api/v1/songs/{$sheet->id}")->assertNoContent();

        $this->assertModelMissing($sheet);
    }

    public function test_you_cannot_delete_a_sheet_you_neither_own_nor_saved(): void
    {
        $sheet = Song::factory()->public()->create();

        $this->actingAs(User::factory()->create())->deleteJson("/api/v1/songs/{$sheet->id}")->assertForbidden();
    }

    public function test_invalid_songs_are_rejected(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/songs/s1', $this->appSong(['key' => 'H', 'sections' => []]))
            ->assertUnprocessable()->assertJsonValidationErrors(['key', 'sections']);
        $this->actingAs($user)->putJson('/api/v1/songs/s1', $this->appSong(['sections' => [['kind' => 'solo', 'label' => 'X']]]))
            ->assertUnprocessable()->assertJsonValidationErrors('sections.0.kind');
    }
}
