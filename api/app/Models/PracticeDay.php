<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Carbon;

/**
 * Minutes played on one day — feeds the groove (streak) and the week chart.
 *
 * @property int $user_id
 * @property Carbon $day
 * @property int $minutes
 */
#[Fillable(['user_id', 'day', 'minutes'])]
class PracticeDay extends Model
{
    public $timestamps = false;

    public $incrementing = false;

    protected $primaryKey = null;

    protected function casts(): array
    {
        return ['day' => 'date'];
    }
}
