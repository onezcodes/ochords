<?php

namespace App\Http\Resources;

use App\Models\Song;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `Song`. The private note and the public flag are only shown to the owner;
 * other players see who uploaded it instead.
 *
 * @mixin Song
 */
class SongResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        $isOwner = $request->user()?->id === $this->user_id;

        return [
            'id' => $this->id,
            'title' => $this->title,
            'artist' => $this->artist,
            'key' => $this->key,
            'tempo' => $this->tempo,
            'sections' => $this->sections,
            'source' => $this->source->value,
            'uploader' => $this->when(! $isOwner, fn () => new UserSummaryResource($this->owner)),
            'versionName' => $this->version_name,
            'rating' => $this->saves_avg_rating !== null ? round((float) $this->saves_avg_rating, 1) : null,
            'plays' => $this->is_public ? $this->plays : null,
            'isMyanmar' => $this->is_myanmar,
            'isPublic' => $this->when($isOwner, $this->is_public),
            'addedAt' => $this->added_at->toIso8601ZuluString(),
            'myNote' => $this->when($isOwner, $this->my_note),
        ];
    }
}
