<?php

namespace App\Services\Ocr;

/**
 * A word and its box, normalised to 0–1 of the page with the origin top-left (the app's convention).
 */
final readonly class OcrWord
{
    public function __construct(
        public string $text,
        public float $x,
        public float $y,
        public float $width,
        public float $height,
    ) {}
}
