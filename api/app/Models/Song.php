<?php

namespace App\Models;

use App\Enums\SongSource;
use Database\Factories\SongFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\Pivot;
use Illuminate\Support\Carbon;

/**
 * A chord sheet. `sections` holds the app's SongSection list as JSON (ChordPro lines, bars, flow).
 *
 * @property string $id
 * @property int $user_id
 * @property string $title
 * @property string $artist
 * @property string $key
 * @property int $tempo
 * @property list<array<string, mixed>> $sections
 * @property SongSource $source
 * @property ?string $version_name
 * @property string $difficulty
 * @property bool $is_myanmar
 * @property bool $is_public
 * @property ?string $my_note
 * @property int $plays
 * @property int $comments_count
 * @property Carbon $added_at
 * @property-read ?int $saves_count
 * @property-read ?float $saves_avg_rating
 * @property-read ?Pivot $pivot  when loaded through User::savedSongs()
 */
#[Fillable([
    'id', 'title', 'artist', 'key', 'tempo', 'sections', 'source', 'version_name', 'difficulty',
    'is_myanmar', 'is_public', 'my_note', 'added_at',
])]
class Song extends Model
{
    /** @use HasFactory<SongFactory> */
    use HasFactory;

    public $incrementing = false;

    protected $keyType = 'string';

    protected function casts(): array
    {
        return [
            'sections' => 'array',
            'source' => SongSource::class,
            'is_myanmar' => 'boolean',
            'is_public' => 'boolean',
            'my_note' => 'encrypted',
            'added_at' => 'datetime',
        ];
    }

    /** @return BelongsTo<User, $this> */
    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    /**
     * Players who saved this sheet into their library.
     *
     * @return BelongsToMany<User, $this>
     */
    public function savers(): BelongsToMany
    {
        return $this->belongsToMany(User::class)->withPivot('rating')->withTimestamps();
    }

    /** @param Builder<Song> $query */
    public function scopePublic(Builder $query): void
    {
        $query->where('is_public', true);
    }

    /**
     * Adds the counts the community listing shows.
     *
     * @param  Builder<Song>  $query
     */
    public function scopeWithCommunityStats(Builder $query): void
    {
        $query->with('owner')
            ->withCount(['savers as saves_count'])
            ->withAvg('savers as saves_avg_rating', 'song_user.rating');
    }
}
