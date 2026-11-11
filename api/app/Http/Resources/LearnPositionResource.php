<?php

namespace App\Http\Resources;

use App\Models\LearnPosition;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `LearnPosition` with its frets. Each fret carries `state` and `stars`, worked out
 * per player by LearnService and set on the model as `progress_state` / `progress_stars`.
 *
 * @mixin LearnPosition
 */
class LearnPositionResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'number' => $this->number,
            'title' => $this->title,
            'subtitle' => $this->subtitle,
            'frets' => $this->frets->map(fn ($fret) => [
                'id' => $fret->id,
                'title' => $fret->title,
                'state' => $fret->getAttribute('progress_state'),
                'kind' => $fret->kind,
                'stars' => (int) $fret->getAttribute('progress_stars'),
            ])->values(),
        ];
    }
}
