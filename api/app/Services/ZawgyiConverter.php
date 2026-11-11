<?php

namespace App\Services;

use RuntimeException;
use Transliterator;

/**
 * Zawgyi is the older, non-standard Myanmar font encoding many sheets were typed in. Songs are always
 * stored as Unicode. Detection uses encoding patterns that never occur in well-formed Unicode Burmese;
 * conversion is ICU's CLDR "Zawgyi-my" transform (PHP intl). Same rules as the app's `Zawgyi`.
 */
class ZawgyiConverter
{
    /** Patterns that never occur in well-formed Unicode Burmese: one is enough. */
    private const MARKERS = [
        // U+1039 as Zawgyi's killer mark; in Unicode it always stacks the next consonant.
        '/\x{1039}(?![\x{1000}-\x{1021}])/u',
        // ေ or ya-yit typed before the consonant, at the start of a word.
        '/(?:^|[^\x{1000}-\x{109F}])[\x{1031}\x{103B}]/u',
    ];

    /** Zawgyi's stacked and variant letter forms — but real Mon/Shan letters in Unicode, so it takes two. */
    private const VARIANT_FORMS = '/[\x{1060}-\x{1097}]/u';

    private ?Transliterator $transliterator = null;

    public function isZawgyi(string $text): bool
    {
        foreach (self::MARKERS as $marker) {
            if (preg_match($marker, $text)) {
                return true;
            }
        }

        return preg_match_all(self::VARIANT_FORMS, $text) >= 2;
    }

    public function toUnicode(string $text): string
    {
        if (! preg_match('/[\x{1000}-\x{109F}]/u', $text)) {
            return $text;
        }
        $this->transliterator ??= Transliterator::create('Zawgyi-my')
            ?? throw new RuntimeException('ICU "Zawgyi-my" transform is missing (PHP intl with ICU ≥ 60 is required).');

        $converted = $this->transliterator->transliterate($text);

        return $converted === false ? $text : $converted;
    }
}
