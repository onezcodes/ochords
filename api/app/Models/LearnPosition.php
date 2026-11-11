<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * @property string $id
 * @property int $number
 * @property string $title
 * @property string $subtitle
 * @property-read Collection<int, LearnFret> $frets
 */
#[Fillable(['id', 'number', 'title', 'subtitle'])]
class LearnPosition extends Model
{
    public $timestamps = false;

    public $incrementing = false;

    protected $keyType = 'string';

    /** @return HasMany<LearnFret, $this> */
    public function frets(): HasMany
    {
        return $this->hasMany(LearnFret::class)->orderBy('sort');
    }
}
