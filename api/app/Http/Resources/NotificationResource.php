<?php

namespace App\Http\Resources;

use App\Models\AppNotification;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `AppNotification`. `time` is ready to show ("5 minutes ago"); `createdAt` is for sorting.
 *
 * @mixin AppNotification
 */
class NotificationResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'id' => (string) $this->id,
            'category' => $this->category->value,
            'title' => $this->title,
            'body' => $this->body,
            'time' => $this->created_at->diffForHumans(),
            'createdAt' => $this->created_at->toIso8601ZuluString(),
            'unread' => $this->read_at === null,
            'systemImage' => $this->system_image,
        ];
    }
}
