<?php

namespace App\Services\Ocr;

/**
 * Reads text from a photo of a chord sheet, with each word's position on the page.
 */
interface OcrEngine
{
    /** Short name sent back to the app, e.g. "google". */
    public function name(): string;

    /**
     * @param  string  $image  JPEG or PNG bytes
     * @return list<OcrLine> lines in reading order
     *
     * @throws ScanUnavailableException when the engine can't be reached or isn't set up
     */
    public function read(string $image): array;
}
