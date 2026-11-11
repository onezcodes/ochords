<?php

namespace App\Models;

use App\Enums\NotificationCategory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;

/**
 * An in-app notification (the app's Notifications screen), separate from push notifications.
 *
 * @property int $id
 * @property int $user_id
 * @property NotificationCategory $category
 * @property string $title
 * @property string $body
 * @property string $system_image
 * @property ?Carbon $read_at
 * @property Carbon $created_at
 */
#[Fillable(['category', 'title', 'body', 'system_image', 'read_at'])]
class AppNotification extends Model
{
    protected function casts(): array
    {
        return [
            'category' => NotificationCategory::class,
            'read_at' => 'datetime',
        ];
    }

    /** @return BelongsTo<User, $this> */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
