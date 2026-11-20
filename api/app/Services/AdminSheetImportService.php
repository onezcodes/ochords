<?php

namespace App\Services;

use App\Enums\SongSource;
use App\Models\Song;
use App\Models\User;
use App\Services\Ocr\ScanUnavailableException;
use App\Services\SheetParsing\ChordSheetParser;
use App\Services\SheetParsing\OcrToScanLines;
use App\Services\SheetParsing\ParsedSheet;
use App\Services\SheetParsing\ScanTextLine;
use App\Services\SheetParsing\ScanWord;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/**
 * Staff flow: photo/PDF → prepare → OCR → ChordSheetParser → catalog-owned song.
 */
class AdminSheetImportService
{
    public function __construct(
        private readonly ScanService $scans,
        private readonly SongService $songs,
        private readonly ZawgyiConverter $zawgyi,
        private readonly AdminAuditService $audit,
        private readonly CatalogOwnerService $catalog,
        private readonly SheetImagePreparer $images,
    ) {}

    /**
     * @param  array{title?: ?string, artist?: ?string, is_public?: bool, difficulty?: ?string, crop?: bool}  $options
     */
    public function importFromUpload(User $staff, UploadedFile $image, array $options = []): AdminSheetImportResult
    {
        $jpeg = $this->images->toJpeg($image, [
            'crop' => array_key_exists('crop', $options) ? (bool) $options['crop'] : true,
        ]);

        try {
            $ocrLines = $this->scans->readForStaff($staff, $jpeg);
        } catch (ScanUnavailableException $e) {
            throw ValidationException::withMessages(['image' => $e->getMessage()]);
        }

        $readsMyanmar = $this->scans->engineName() === 'google';
        $lines = $this->unicodeLines(OcrToScanLines::convert($ocrLines));
        $parsed = ChordSheetParser::parse($lines, $readsMyanmar);

        if ($this->usedChords($parsed) === []) {
            throw ValidationException::withMessages([
                'image' => 'No chords were found on that sheet. Try a clearer photo, or check that server scanning is on.',
            ]);
        }

        $draft = $parsed->song;
        $title = trim((string) ($options['title'] ?? '')) ?: (string) ($draft['title'] ?? '');
        $artist = trim((string) ($options['artist'] ?? '')) ?: (string) ($draft['artist'] ?? '');
        if ($title === '') {
            $title = 'Untitled scan';
        }
        if ($artist === '') {
            $artist = 'Unknown';
        }

        $isPublic = (bool) ($options['is_public'] ?? false);
        $id = (string) ($draft['id'] ?? Str::uuid());
        $owner = $this->catalog->owner();

        $song = $this->songs->save($owner, null, $id, [
            'title' => $title,
            'artist' => $artist,
            'key' => (string) ($draft['key'] ?? 'C'),
            'tempo' => (int) ($draft['tempo'] ?? 80),
            'sections' => $draft['sections'] ?? [],
            'source' => $isPublic ? SongSource::Community->value : SongSource::Imported->value,
            'isMyanmar' => (bool) ($draft['isMyanmar'] ?? false),
            'myNote' => $draft['myNote'] ?? null,
            'isPublic' => $isPublic,
        ]);

        if (! empty($options['difficulty'])) {
            $song->forceFill(['difficulty' => (string) $options['difficulty']])->save();
        }

        $this->audit->log('sheet.imported_from_scan', $song, [
            'engine' => $this->scans->engineName(),
            'lines' => count($ocrLines),
            'sections' => count($song->sections ?? []),
            'is_public' => $song->is_public,
            'owner_handle' => $owner->handle,
            'low_confidence' => array_values($parsed->lowConfidence),
            'needs_lyrics' => count($parsed->needsLyrics),
        ], $staff);

        return new AdminSheetImportResult(
            song: $song->fresh() ?? $song,
            lowConfidence: array_values($parsed->lowConfidence),
            needsLyrics: count($parsed->needsLyrics),
            chordNotes: $parsed->chordNotes,
        );
    }

    /**
     * @param  list<ScanTextLine>  $lines
     * @return list<ScanTextLine>
     */
    private function unicodeLines(array $lines): array
    {
        $blob = implode("\n", array_map(static fn (ScanTextLine $line): string => $line->text(), $lines));
        if (! $this->zawgyi->isZawgyi($blob)) {
            return $lines;
        }

        return array_map(function (ScanTextLine $line): ScanTextLine {
            return new ScanTextLine(
                array_map(
                    fn (ScanWord $word): ScanWord => new ScanWord($this->zawgyi->toUnicode($word->text), $word->box),
                    $line->words,
                ),
                $line->confidence,
            );
        }, $lines);
    }

    /** @return list<string> */
    private function usedChords(ParsedSheet $parsed): array
    {
        $names = [];
        foreach ($parsed->song['sections'] ?? [] as $section) {
            foreach ($section['bars'] ?? [] as $bar) {
                if (is_string($bar) && $bar !== '') {
                    $names[$bar] = true;
                }
            }
            foreach ($section['lines'] ?? [] as $line) {
                if (! is_string($line)) {
                    continue;
                }
                if (preg_match_all('/\[([^\]]+)\]/', $line, $matches) > 0) {
                    foreach ($matches[1] as $chord) {
                        $names[$chord] = true;
                    }
                }
            }
        }

        return array_keys($names);
    }
}
