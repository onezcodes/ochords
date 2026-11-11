<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;

/**
 * @property string $id
 * @property string $learn_fret_id
 * @property int $sort
 * @property string $kind
 * @property string $prompt
 * @property string $detail
 * @property list<string> $options
 * @property string $answer
 */
#[Fillable(['id', 'learn_fret_id', 'sort', 'kind', 'prompt', 'detail', 'options', 'answer'])]
class LessonQuestion extends Model
{
    public $timestamps = false;

    public $incrementing = false;

    protected $keyType = 'string';

    protected function casts(): array
    {
        return ['options' => 'array'];
    }
}
