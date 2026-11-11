<?php

namespace App\Http\Requests\Songs;

use App\Enums\SectionKind;
use App\Enums\SongSource;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

/**
 * A song as the app encodes it. The app sends the whole song even when saving another player's
 * public sheet; then only the ID is used (see SongService::save).
 */
class SaveSongRequest extends FormRequest
{
    private const MAX_SECTIONS = 60;

    private const MAX_LINES_PER_SECTION = 120;

    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'title' => ['required', 'string', 'max:200'],
            'artist' => ['nullable', 'string', 'max:200'],
            'key' => ['required', 'string', 'regex:/^[A-G][#b]?m?$/'],
            'tempo' => ['nullable', 'integer', 'between:20,300'],
            'source' => ['nullable', Rule::enum(SongSource::class)],
            'versionName' => ['nullable', 'string', 'max:100'],
            'isMyanmar' => ['nullable', 'boolean'],
            'isPublic' => ['nullable', 'boolean'],
            'myNote' => ['nullable', 'string', 'max:1000'],
            'addedAt' => ['nullable', 'date'],
            'sections' => ['required', 'array', 'min:1', 'max:'.self::MAX_SECTIONS],
            'sections.*.id' => ['nullable', 'string', 'max:64'],
            'sections.*.kind' => ['required', Rule::enum(SectionKind::class)],
            'sections.*.label' => ['required', 'string', 'max:40'],
            'sections.*.note' => ['nullable', 'string', 'max:100'],
            'sections.*.strum' => ['nullable', 'string', 'max:60'],
            'sections.*.lines' => ['nullable', 'array', 'max:'.self::MAX_LINES_PER_SECTION],
            'sections.*.lines.*' => ['string', 'max:500'],
            'sections.*.bars' => ['nullable', 'array', 'max:64'],
            'sections.*.bars.*' => ['string', 'max:16'],
            'sections.*.flow' => ['nullable', 'array', 'max:20'],
            'sections.*.flow.*.kind' => ['required', Rule::enum(SectionKind::class)],
            'sections.*.flow.*.label' => ['required', 'string', 'max:40'],
        ];
    }
}
