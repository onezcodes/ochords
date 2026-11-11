<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * @property string $id
 * @property int $user_id
 * @property string $name
 * @property string $note
 * @property ?string $date shown on the card in place of the note; refreshed to the last edit date on write
 */
#[Fillable(['id', 'name', 'note', 'date'])]
class Setlist extends Model
{
    public $incrementing = false;

    protected $keyType = 'string';

    /** @return BelongsTo<User, $this> */
    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    /**
     * Song IDs in play order. Songs may be another player's public sheet, so this is a plain list
     * rather than a relation that would hide songs the owner doesn't own.
     *
     * @return list<string>
     */
    public function songIds(): array
    {
        return $this->getConnection()->table('setlist_song')
            ->where('setlist_id', $this->id)
            ->orderBy('position')
            ->pluck('song_id')
            ->all();
    }

    /** @param list<string> $songIds */
    public function syncSongIds(array $songIds): void
    {
        $table = $this->getConnection()->table('setlist_song');
        $table->where('setlist_id', $this->id)->delete();
        $rows = array_map(
            fn (string $songId, int $position) => ['setlist_id' => $this->id, 'song_id' => $songId, 'position' => $position],
            $songIds,
            array_keys($songIds),
        );
        $table->insert($rows);
    }
}
