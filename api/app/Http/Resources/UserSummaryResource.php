<?php

namespace App\Http\Resources;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `UserSummary`: a player as others see them.
 *
 * @mixin User
 */
class UserSummaryResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'id' => (string) $this->id,
            'name' => $this->name,
            'handle' => $this->handle,
            'tier' => $this->tier()->value,
            'sheets' => (int) ($this->public_songs_count ?? $this->songs()->where('is_public', true)->count()),
            'followers' => (int) ($this->followers_count ?? $this->followers()->count()),
            'city' => $this->city,
        ];
    }
}
