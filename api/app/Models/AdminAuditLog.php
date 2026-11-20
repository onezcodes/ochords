<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;

/**
 * Staff action trail for /admin. Meta holds IDs/enums only — never personal free text.
 *
 * @property int $id
 * @property ?int $actor_id
 * @property string $action
 * @property ?string $subject_type
 * @property ?string $subject_id
 * @property ?array<string, mixed> $meta
 * @property ?string $ip
 * @property Carbon $created_at
 */
class AdminAuditLog extends Model
{
    public $timestamps = true;

    const UPDATED_AT = null;

    protected $fillable = [
        'actor_id', 'action', 'subject_type', 'subject_id', 'meta', 'ip',
    ];

    protected function casts(): array
    {
        return [
            'meta' => 'array',
            'created_at' => 'datetime',
        ];
    }

    /** @return BelongsTo<User, $this> */
    public function actor(): BelongsTo
    {
        return $this->belongsTo(User::class, 'actor_id');
    }
}
