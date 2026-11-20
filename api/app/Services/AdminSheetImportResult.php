<?php

namespace App\Services;

use App\Models\Song;

/** Outcome of a staff scan import, including what the editor should double-check. */
final readonly class AdminSheetImportResult
{
    /**
     * @param  list<string>  $lowConfidence
     * @param  array<string, string>  $chordNotes
     */
    public function __construct(
        public Song $song,
        public array $lowConfidence = [],
        public int $needsLyrics = 0,
        public array $chordNotes = [],
    ) {}
}
