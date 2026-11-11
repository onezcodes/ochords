<?php

namespace App\Models;

use App\Enums\ReportReason;
use App\Enums\ReportResolution;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;

/**
 * A player's report on a sheet. Open until a moderator resolves it (`ochords:reports:resolve`);
 * resolved reports are purged after the retention period.
 *
 * @property int $id
 * @property int $user_id
 * @property string $song_id
 * @property ReportReason $reason
 * @property ?string $detail
 * @property string $status
 * @property ?Carbon $resolved_at
 * @property ?ReportResolution $resolution
 * @property Carbon $created_at
 */
#[Fillable(['song_id', 'reason', 'detail'])]
class Report extends Model
{
    protected function casts(): array
    {
        return [
            'reason' => ReportReason::class,
            'detail' => 'encrypted',
            'resolved_at' => 'datetime',
            'resolution' => ReportResolution::class,
        ];
    }

    /** @param Builder<Report> $query */
    public function scopeOpen(Builder $query): void
    {
        $query->whereNull('resolved_at');
    }

    /** @return BelongsTo<Song, $this> */
    public function song(): BelongsTo
    {
        return $this->belongsTo(Song::class);
    }

    /** @return BelongsTo<User, $this> */
    public function reporter(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
