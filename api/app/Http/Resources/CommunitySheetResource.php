<?php

namespace App\Http\Resources;

use App\Models\Song;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `CommunitySheet`: a public song with its Explore stats.
 * "votes" are saves — how many players added the sheet to their library.
 *
 * @mixin Song
 */
class CommunitySheetResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'song' => new SongResource($this->resource),
            'votes' => (int) ($this->saves_count ?? 0),
            'comments' => $this->comments_count,
            'difficulty' => $this->difficulty,
        ];
    }
}
