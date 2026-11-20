<?php

namespace App\Services;

use App\Enums\SongSource;
use App\Models\Song;
use App\Models\User;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class SongService
{
    private const LIKE_TITLE = "title like ? escape '!'";

    private const LIKE_ARTIST = "artist like ? escape '!'";

    private const LIKE_NAME = "name like ? escape '!'";

    public function __construct(private readonly ZawgyiConverter $zawgyi) {}

    /**
     * The library: the player's own sheets plus public sheets they saved, newest first.
     *
     * @return Collection<int, Song>
     */
    public function library(User $user): Collection
    {
        return Song::query()
            ->withCommunityStats()
            ->where(fn (Builder $q) => $q
                ->where('user_id', $user->id)
                ->orWhereHas('savers', fn (Builder $s) => $s->where('users.id', $user->id)))
            ->orderByDesc('added_at')
            ->get();
    }

    /**
     * PUT semantics keyed by the app's song ID: create or update the player's own sheet, or —
     * when the ID belongs to someone else's public sheet — add that sheet to their library.
     * The caller checks SongPolicy::save on an existing song first.
     *
     * @param  array<string, mixed>  $data  validated SaveSongRequest data
     */
    public function save(User $user, ?Song $existing, string $id, array $data): Song
    {
        if ($existing && $existing->user_id !== $user->id) {
            $existing->savers()->syncWithoutDetaching([$user->id]);

            return $this->withStats($existing);
        }

        $song = $existing ?? new Song(['id' => $id]);
        $song->fill($this->attributes($data, $song->exists));
        $song->user_id = $user->id;
        $song->save();

        return $this->withStats($song);
    }

    /**
     * Deletes the player's own sheet (every setlist drops it, by foreign key), or takes a saved sheet
     * out of their library and their setlists.
     */
    public function remove(User $user, Song $song): void
    {
        if ($song->user_id === $user->id) {
            $song->delete();

            return;
        }
        $song->savers()->detach($user->id);
        DB::table('setlist_song')
            ->where('song_id', $song->id)
            ->whereIn('setlist_id', $user->setlists()->select('id'))
            ->delete();
    }

    /**
     * Public sheets matching title, artist or uploader name; most-saved first.
     *
     * @return Collection<int, Song>
     */
    public function explore(?string $query): Collection
    {
        $term = trim((string) $query);
        $pattern = '%'.$this->escapeLike($term).'%';

        return Song::query()
            ->public()
            ->withCommunityStats()
            ->when($term !== '', fn (Builder $q) => $q->where(fn (Builder $w) => $w
                ->whereRaw(self::LIKE_TITLE, [$pattern])
                ->orWhereRaw(self::LIKE_ARTIST, [$pattern])
                ->orWhereHas('owner', fn (Builder $o) => $o->whereRaw(self::LIKE_NAME, [$pattern]))))
            ->orderByRaw('featured_at IS NULL')
            ->orderByDesc('featured_at')
            ->orderByDesc('saves_count')
            ->orderByDesc('added_at')
            ->limit((int) config('ochords.explore_limit'))
            ->get();
    }

    /**
     * The most-played public sheets.
     *
     * @return Collection<int, Song>
     */
    public function popular(): Collection
    {
        return Song::query()
            ->public()
            ->withCommunityStats()
            ->orderByRaw('featured_at IS NULL')
            ->orderByDesc('featured_at')
            ->orderByDesc('plays')
            ->orderByDesc('saves_count')
            ->limit((int) config('ochords.explore_limit'))
            ->get();
    }

    public function withStats(Song $song): Song
    {
        return Song::query()->withCommunityStats()->findOrFail($song->id);
    }

    /**
     * Maps the app's camelCase song onto columns, and gives every section the keys the app needs to decode it.
     *
     * @param  array<string, mixed>  $data
     * @return array<string, mixed>
     */
    private function attributes(array $data, bool $exists): array
    {
        $attributes = [
            'title' => $data['title'],
            'artist' => $data['artist'] ?? '',
            'key' => $data['key'],
            'tempo' => $data['tempo'] ?? 80,
            'sections' => array_map($this->normalisedSection(...), $data['sections']),
            'source' => $data['source'] ?? SongSource::Imported->value,
            'version_name' => $data['versionName'] ?? null,
            'is_myanmar' => $data['isMyanmar'] ?? true,
            'my_note' => $data['myNote'] ?? null,
        ];
        // Leaving `isPublic` out keeps the current setting, so an older app version can't unshare a sheet.
        if (array_key_exists('isPublic', $data) && $data['isPublic'] !== null) {
            $attributes['is_public'] = (bool) $data['isPublic'];
        }
        if (! $exists) {
            $attributes['added_at'] = $data['addedAt'] ?? now();
        }

        return $this->inUnicode($attributes);
    }

    /**
     * Songs are stored as Unicode: a sheet typed in Zawgyi (judged on all its text together) is converted.
     *
     * @param  array<string, mixed>  $attributes
     * @return array<string, mixed>
     */
    private function inUnicode(array $attributes): array
    {
        $sections = $attributes['sections'];
        $text = implode("\n", [
            $attributes['title'], $attributes['artist'], (string) $attributes['my_note'],
            ...array_map(fn (array $s) => $s['label'].' '.$s['note'].' '.implode("\n", $s['lines']), $sections),
        ]);
        if (! $this->zawgyi->isZawgyi($text)) {
            return $attributes;
        }

        $convert = fn (?string $value) => $value === null ? null : $this->zawgyi->toUnicode($value);
        $attributes['title'] = $convert($attributes['title']);
        $attributes['artist'] = $convert($attributes['artist']);
        $attributes['my_note'] = $convert($attributes['my_note']);
        $attributes['sections'] = array_map(fn (array $s) => [
            ...$s,
            'label' => $convert($s['label']),
            'note' => $convert($s['note']),
            'lines' => array_map($convert, $s['lines']),
        ], $sections);

        return $attributes;
    }

    /**
     * @param  array<string, mixed>  $section
     * @return array<string, mixed>
     */
    private function normalisedSection(array $section): array
    {
        return [
            'id' => $section['id'] ?? (string) Str::uuid(),
            'kind' => $section['kind'],
            'label' => $section['label'],
            'note' => $section['note'] ?? null,
            'strum' => $section['strum'] ?? null,
            'lines' => array_values($section['lines'] ?? []),
            'bars' => array_values($section['bars'] ?? []),
            'flow' => array_values(array_map(
                fn (array $step) => ['kind' => $step['kind'], 'label' => $step['label']],
                $section['flow'] ?? [],
            )),
        ];
    }

    /** Escapes LIKE wildcards with "!", declared in the query so MySQL, SQLite and Postgres agree. */
    private function escapeLike(string $term): string
    {
        return str_replace(['!', '%', '_'], ['!!', '!%', '!_'], $term);
    }
}
