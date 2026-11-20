<?php

namespace App\Services\SheetParsing;

final class ScanTextLine
{
    /** @param list<ScanWord> $words */
    public function __construct(
        public readonly array $words,
        public readonly float $confidence,
    ) {}

    public function text(): string
    {
        return implode(' ', array_map(fn (ScanWord $w) => $w->text, $this->words));
    }

    public function box(): Box
    {
        if (empty($this->words)) {
            return Box::zero();
        }
        $first = $this->words[0]->box;
        $rest = array_slice($this->words, 1);

        return array_reduce($rest, fn (Box $acc, ScanWord $w) => $acc->union($w->box), $first);
    }
}
