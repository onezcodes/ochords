<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\Pivot;

/**
 * One lesson on the learning path ("fret").
 *
 * @property string $id
 * @property string $learn_position_id
 * @property int $sort
 * @property string $title
 * @property string $kind
 * @property-read ?Pivot $pivot  when loaded through User::completedFrets()
 */
#[Fillable(['id', 'learn_position_id', 'sort', 'title', 'kind'])]
class LearnFret extends Model
{
    public $timestamps = false;

    public $incrementing = false;

    protected $keyType = 'string';

    /** @return BelongsTo<LearnPosition, $this> */
    public function position(): BelongsTo
    {
        return $this->belongsTo(LearnPosition::class, 'learn_position_id');
    }

    /** @return HasMany<LessonQuestion, $this> */
    public function questions(): HasMany
    {
        return $this->hasMany(LessonQuestion::class)->orderBy('sort');
    }
}
