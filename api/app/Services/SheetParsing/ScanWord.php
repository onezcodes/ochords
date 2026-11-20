<?php

namespace App\Services\SheetParsing;

final readonly class ScanWord
{
    public function __construct(
        public string $text,
        public Box $box,
    ) {}
}
