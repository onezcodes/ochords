<?php

namespace App\Services\Ocr;

/**
 * One line of text as the app's parser expects it (its `ScanTextLine`).
 */
final readonly class OcrLine
{
    /** @param list<OcrWord> $words */
    public function __construct(
        public array $words,
        public float $confidence,
    ) {}
}
