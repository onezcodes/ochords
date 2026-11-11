<?php

namespace App\Models;

use App\Enums\FeedbackKind;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Carbon;

/**
 * @property int $id
 * @property ?int $user_id
 * @property FeedbackKind $kind
 * @property string $message
 * @property Carbon $created_at
 */
#[Fillable(['user_id', 'kind', 'message'])]
class Feedback extends Model
{
    protected $table = 'feedback';

    protected function casts(): array
    {
        return [
            'kind' => FeedbackKind::class,
            'message' => 'encrypted',
        ];
    }
}
