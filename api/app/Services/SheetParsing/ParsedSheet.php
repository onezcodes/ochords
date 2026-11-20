<?php

namespace App\Services\SheetParsing;

/**
 * The result of ChordSheetParser::parse(), as an array-shaped song draft ready for SongService,
 * plus review metadata.
 *
 * $song shape: { id, title, artist, key, tempo, sections[], isMyanmar, myNote }
 * sections[]: { id, kind, label, note, strum, lines[], bars[], flow[] }
 *
 * $needsLyrics: list of { sectionId, line } for lines the OCR couldn't read
 * $regions: list of { kind, x, y, width, height } for each section's page area
 */
final readonly class ParsedSheet
{
    /**
     * @param  array<string,mixed>  $song
     * @param  list<string>  $lowConfidence
     * @param  array<string,string>  $chordNotes
     * @param  list<array{sectionId:string,line:int}>  $needsLyrics
     * @param  list<array{kind:string,x:float,y:float,width:float,height:float}>  $regions
     */
    public function __construct(
        public array $song,
        public array $lowConfidence = [],
        public array $chordNotes = [],
        public array $needsLyrics = [],
        public array $regions = [],
    ) {}
}
