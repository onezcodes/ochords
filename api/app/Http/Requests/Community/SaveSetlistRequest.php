<?php

namespace App\Http\Requests\Community;

use Illuminate\Foundation\Http\FormRequest;

/** A setlist as the app sends it: name, note and the song IDs in play order. */
class SaveSetlistRequest extends FormRequest
{
    private const MAX_SONGS = 200;

    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'name' => ['required', 'string', 'max:80'],
            'note' => ['nullable', 'string', 'max:500'],
            'songIDs' => ['present', 'array', 'max:'.self::MAX_SONGS],
            'songIDs.*' => ['required', 'string', 'regex:/^[A-Za-z0-9\-]{1,64}$/'],
        ];
    }
}
