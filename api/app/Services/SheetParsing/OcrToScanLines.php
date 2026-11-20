<?php

namespace App\Services\SheetParsing;

use App\Services\Ocr\OcrLine;
use App\Services\Ocr\OcrWord;

final class OcrToScanLines
{
    /**
     * Converts a list of OcrLine DTOs (from GoogleVisionOcr / DisabledOcr) into the ScanTextLine
     * format the ChordSheetParser expects.
     *
     * @param  list<OcrLine>  $lines
     * @return list<ScanTextLine>
     */
    public static function convert(array $lines): array
    {
        return array_map(fn (OcrLine $line) => new ScanTextLine(
            array_map(
                fn (OcrWord $w) => new ScanWord($w->text, new Box($w->x, $w->y, $w->width, $w->height)),
                $line->words,
            ),
            $line->confidence,
        ), $lines);
    }
}
