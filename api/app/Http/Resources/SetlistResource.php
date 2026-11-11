<?php

namespace App\Http\Resources;

use App\Models\Setlist;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `Setlist`.
 *
 * @mixin Setlist
 */
class SetlistResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'note' => $this->note,
            'songIDs' => $this->songIds(),
            'date' => $this->date,
        ];
    }
}
