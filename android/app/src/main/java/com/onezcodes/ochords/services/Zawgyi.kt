package com.onezcodes.ochords.services

import android.icu.text.Transliterator

/**
 * Zawgyi is the older, non-standard Myanmar font encoding many sheets were typed in. Detection is by encoding
 * patterns that never occur in well-formed Unicode Burmese; conversion is ICU's CLDR "Zawgyi-my" transform
 * (built into Android's ICU since API 28; a no-op fallback below that). Keep the rules in step with the server's
 * `ZawgyiConverter` and iOS's `Zawgyi.swift`.
 */
object Zawgyi {
    /** Patterns that never occur in well-formed Unicode Burmese: one is enough. */
    private val markers = listOf(
        // U+1039 as Zawgyi's killer mark (asat); in Unicode it always stacks the next consonant.
        Regex("\\u1039(?![\\u1000-\\u1021])"),
        // ေ or ya-yit typed before the consonant, at the start of a word.
        Regex("(?:^|[^\\u1000-\\u109F])[\\u1031\\u103B]"),
    )

    /** Zawgyi's stacked and variant letter forms — but real Mon/Shan letters in Unicode, so it takes two. */
    private val variantForms = Regex("[\\u1060-\\u1097]")

    /** Signs of well-formed Unicode that Zawgyi text doesn't have. */
    private val unicodeSigns = listOf(
        Regex("\\u103E"),
        Regex("\\u1004\\u103A\\u1039"),
        Regex("[\\u1000-\\u1021]\\u103A(?=[\\s\\u1000-\\u1021]|$)"),
    )

    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Zawgyi-my") }.getOrNull()
    }

    /** How many Zawgyi patterns `text` has (0 for well-formed Unicode). */
    fun markerCount(text: String): Int = (markers + variantForms).sumOf { it.findAll(text).count() }

    /** Zawgyi when its patterns outweigh the signs of Unicode. */
    fun isZawgyi(text: String): Boolean {
        val variants = variantForms.findAll(text).count()
        val zawgyi = markers.sumOf { it.findAll(text).count() } + (if (variants >= 2) variants else 0)
        val unicode = unicodeSigns.sumOf { it.findAll(text).count() }
        return zawgyi > unicode
    }

    /** Unicode for Zawgyi text; leaves text without Myanmar letters untouched. */
    fun toUnicode(text: String): String {
        if (text.none { it.code in 0x1000..0x109F }) return text
        return transliterator?.transliterate(text) ?: text
    }

    /** Converts `text` only if it looks like Zawgyi — for things people type (their keyboard may be Zawgyi). */
    fun normalised(text: String): String = if (isZawgyi(text)) toUnicode(text) else text
}
