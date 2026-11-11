package com.onezcodes.ochords.services

import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A draft song read from positioned text, plus what the user should check. */
data class ParsedSheet(
    val song: Song,
    val lowConfidence: List<String> = emptyList(),
    val chordNotes: Map<String, String> = emptyMap(),
    val needsLyrics: List<ScanLineRef> = emptyList(),
    val regions: List<ScanRegion> = emptyList(),
)

/**
 * Turns OCR / PDF text lines into sections of ChordPro lines ("[C]lyric [G/B]lyric").
 *
 * Layout rules: a row that is mostly chord names is a chord row; it pairs with the lyric row right below it,
 * and each chord lands on the syllable under its x position. Rows like "Chorus", "Verse 2:", "Intro: C G Am F"
 * start sections. Rows before the first section/chord row hold the title, artist and "Key / Capo / BPM".
 */
object ChordSheetParser {

    fun parse(lines: List<ScanTextLine>, readsMyanmar: Boolean): ParsedSheet {
        val rows = sheetFurniture(separateInline(groupRows(lines.map(::splittingGluedChords)).map(::withoutStrayDots).map(::classify)))
        val state = Builder(readsMyanmar)
        state.margin = rows.minOfOrNull { it.box.minX } ?: 0.0
        state.width = (rows.maxOfOrNull { it.box.maxX } ?: 1.0) - state.margin
        var bodyStart = rows.indexOfFirst { it.kind is RowKind.Header || it.kind is RowKind.Chords }.let { if (it < 0) rows.size else it }
        // Only the first four rows can be title/credits/meta (a strumming pattern doesn't count towards them); later
        // rows before the first chord row are lyrics…
        var preambleRows = 4
        // …unless a section label near the top says where the song starts ("V1, …"): everything above it — with no
        // chords to play — is the song's details.
        val label = rows.take(9).indexOfFirst { it.kind is RowKind.Header }.takeIf { it >= 0 }
        if (label != null && rows.subList(0, label).none { it.kind is RowKind.Chords }) {
            bodyStart = label
            preambleRows = label
        } else if (bodyStart < rows.size && rows[bodyStart].kind is RowKind.Chords && label != null && label == bodyStart + 1) {
            // A chord row right above the opening label belongs to the song ("F … Em" over "V1, …").
            preambleRows = bodyStart
        }
        var split = 0
        var counted = 0
        while (split < bodyStart && counted < preambleRows) {
            if (rows[split].kind !is RowKind.Strum) counted += 1
            split += 1
        }
        state.readPreamble(rows.subList(0, split))
        state.readBody(rows.subList(split, rows.size))
        return state.result()
    }

    // MARK: - Chords

    private val chordRegex = Regex("""^[A-G][#b]?(?:maj|min|dim|aug|sus|add|m|M|°|ø|\+)?\d{0,2}(?:(?:maj|sus|add|b|#|\+|-)\d{1,2})*(?:\([^)]*\))?(?:/[A-G][#b]?)?$""")
    private val neutralTokens = setOf("x2", "x3", "x4", "×2", "×3", "×4", "(x2)", "(x3)", "(x4)", "n.c.", "nc", "n.c", "…", "...")

    fun isChord(s: String): Boolean = chordRegex.matches(s)

    /** Characters a reader makes out of bar lines and brackets around chords: "|", "I", "l", "1", "!". */
    private const val barMarks = "|Il1!()[]"

    /**
     * A word in a chord row read with bar lines in mind: only bar marks ("||", "1)", "(I") is a bar line; a chord
     * with bar marks stuck to it ("(IC", "Am|", "G1)") is that chord.
     */
    fun barToken(text: String): Token {
        if (text.all { it in barMarks || it == ':' || it == '/' }) return Token.Bar
        val barNote = "Bar line read as a letter — please check"
        var t = text.trimStart { it in barMarks }
        (token(t) as? Token.Chord)?.let { return Token.Chord(it.name, barNote) }
        while (t.isNotEmpty() && t.last() in barMarks && t.length > 1) {
            t = t.dropLast(1)
            (token(t) as? Token.Chord)?.let { return Token.Chord(it.name, barNote) }
        }
        return Token.Word(text)
    }

    /**
     * A lone "." or "," in a Myanmar line is a speck the reader picked up (often in the gap before a chord written
     * at the line's end), not part of the lyric. An ellipsis ("…", "....") is kept.
     */
    fun withoutStrayDots(row: Row): Row {
        if (row.words.none { isMyanmar(it.text) }) return row
        return row.copy(words = row.words.filterNot { it.text == "." || it.text == "," || it.text == "·" })
    }

    /**
     * Close chords that the reader ran together into one word ("AmEm", "CG/B", "Am7F"), as separate chords:
     * each part a chord, at least one longer than a letter (so a word like "CAB" or "BAD" isn't split).
     */
    fun gluedChords(text: String): List<String>? {
        if (isChord(text) || isMyanmar(text) || text.length < 3) return null
        val n = text.length
        // Fewest parts that cover the word, each a chord.
        val best = arrayOfNulls<List<String>>(n + 1)
        best[0] = emptyList()
        for (end in 1..n) {
            for (start in 0 until end) {
                val before = best[start] ?: continue
                val part = text.substring(start, end)
                if (isChord(part) && (best[end] == null || best[end]!!.size > before.size + 1)) best[end] = before + part
            }
        }
        val parts = best[n] ?: return null
        if (parts.size < 2 || parts.none { it.length > 1 }) return null
        return parts
    }

    /** Splits glued chords into words, each placed across the original word's width by its length. */
    fun splittingGluedChords(line: ScanTextLine): ScanTextLine {
        val words = ArrayList<ScanWord>()
        for (w in line.words) {
            val parts = gluedChords(w.text)
            if (parts == null) { words.add(w); continue }
            val total = parts.sumOf { it.length }.toDouble()
            var at = 0.0
            for (part in parts) {
                val len = part.length.toDouble()
                words.add(ScanWord(part, Box(w.box.minX + w.box.width * at / total, w.box.minY, w.box.width * len / total, w.box.height)))
                at += len
            }
        }
        return ScanTextLine(words, line.confidence)
    }

    sealed class Token {
        data class Chord(val name: String, val note: String? = null) : Token()
        data class Word(val text: String) : Token()
        /** A bar line, repeat mark, "N.C." or other mark that is neither a chord nor a word (iOS `.neutral`). */
        data object Bar : Token()
    }

    /** Classifies one token, fixing common OCR slips ("am" → Am, "Arn" → Am, "G/8" → G/B, "GB" → G/B). */
    fun token(raw: String): Token {
        if (raw == UNREADABLE_TEXT || raw.lowercase() in neutralTokens || raw.all { it in "|/-–—.:*%" }) return Token.Bar
        var t = raw.replace("♯", "#").replace("♭", "b")
        t = t.trim { it in "|[],.:;" }
        // Hold marks after a chord ("D-", "D…", "G~") — the chord is still D or G.
        if (t.length > 1 && t.first() in "ABCDEFG") t = t.trimEnd { it in "-–—~…" }
        if (t.startsWith("(") && t.endsWith(")") && t.count { it == '(' } == 1) t = t.drop(1).dropLast(1)
        if (t.isEmpty()) return Token.Bar
        if (isChord(t)) return Token.Chord(t, null)

        var f = t
        if (f.first() in "abcdefg") f = f.first().uppercaseChar() + f.drop(1)
        f = f.replace("rn", "m")
        val slash = f.indexOf('/')
        if (slash >= 0) {
            val bass = f.substring(slash + 1).replace("8", "B").replace("0", "D")
            f = f.substring(0, slash + 1) + bass
        }
        if (isChord(f)) return Token.Chord(f, "Unclear chord — please check it")

        // A small raised bass note ("Gᴮ") often reads as two capitals run together.
        superscriptBassRegex.matchEntire(f)?.let { m ->
            val slashChord = "${m.groupValues[1]}/${m.groupValues[2]}"
            if (isChord(slashChord)) return Token.Chord(slashChord, "Superscript bass note read as a slash chord")
        }
        return Token.Word(raw)
    }

    private val superscriptBassRegex = Regex("""([A-G][#b]?(?:m7|maj7|m|7|sus2|sus4)?)([A-G])""")

    // MARK: - Rows

    data class PlacedChord(val name: String, val x: Double, val note: String? = null)

    sealed class RowKind {
        data class Header(val kind: SectionKind, val label: String?, val number: Int?, val note: String?, val chords: List<PlacedChord>) : RowKind()
        data class Chords(val chords: List<PlacedChord>) : RowKind()
        data object Meta : RowKind()
        data object Lyric : RowKind()
        /** A strumming pattern written as arrows ("↓ ↓ ↓ ↑ ↓"). */
        data class Strum(val pattern: String) : RowKind()
    }

    data class Row(val words: List<ScanWord>, val confidence: Float, val kind: RowKind = RowKind.Lyric) {
        val text: String get() = words.joinToString(" ") { it.text }
        val box: Box get() = words.drop(1).fold(words.firstOrNull()?.box ?: Box.zero) { acc, w -> acc.union(w.box) }
        /** Words that were actually read (no placeholders). */
        val readWords: List<ScanWord> get() = words.filter { it.text != UNREADABLE_TEXT }
        val readText: String get() = readWords.joinToString(" ") { it.text }
    }

    /** Joins pieces that sit on the same baseline (OCR often splits a spaced-out chord row). */
    fun groupRows(lines: List<ScanTextLine>): List<Row> {
        val rows = ArrayList<Row>()
        for (line in lines.sortedBy { it.box.midY }) {
            if (line.words.isEmpty()) continue
            val b = line.box
            val last = rows.lastOrNull()
            if (last != null) {
                val lb = last.box
                val overlap = min(lb.maxY, b.maxY) - max(lb.minY, b.minY)
                if (overlap > 0.5 * min(lb.height, b.height)) {
                    // A second OCR pass can re-read words already on this row; keep the first reading.
                    val fresh = line.words.filter { w ->
                        last.words.none { o ->
                            val i = o.box.intersection(w.box)
                            !i.isEmpty && i.width * i.height > 0.5 * min(o.box.width * o.box.height, w.box.width * w.box.height)
                        }
                    }
                    if (fresh.isEmpty()) continue
                    val unreadable = fresh.all { it.text == UNREADABLE_TEXT }
                    rows[rows.size - 1] = last.copy(
                        words = (last.words + fresh).sortedBy { it.box.minX },
                        confidence = if (unreadable) last.confidence else min(last.confidence, line.confidence),
                    )
                    continue
                }
            }
            rows.add(Row(line.words.sortedBy { it.box.minX }, line.confidence))
        }
        return rows
    }

    private val headerRegex = Regex(
        """^[\[(]?\s*(intro|verse|chorus|refrain|hook|pre[- ]?chorus|bridge|outro|ending|coda|music|interlude|instrumental|solo|break|tag|v|ch|cho)\.?\s*(\d+)?\s*[\])]?\s*(:)?\s*((?:x|×)\s*\d|\((?:x|×)\s*\d\))?""",
        RegexOption.IGNORE_CASE,
    )

    private val metaRegex = Regex("""\b(key|capo|tempo|bpm|time\s*sig|strum)\b""", RegexOption.IGNORE_CASE)

    fun classify(row: Row): Row {
        val text = row.text

        headerRegex.find(text)?.let { m ->
            val name = m.groupValues[1].lowercase()
            val number = m.groups[2]?.value?.toIntOrNull()
            val hasColon = m.groups[3] != null
            val repeatNote = m.groups[4]?.value?.let { "×" + it.filter { c -> c.isDigit() } }
            val short = name in listOf("v", "ch", "cho")
            // Words that end past the matched header ("Verses", "Chorus of angels") are the rest of the row.
            val consumed = m.range.last + 1
            var pos = 0
            val restWords = ArrayList<ScanWord>()
            for (w in row.words) {
                if (pos + w.text.length > consumed) restWords.add(w)
                pos += w.text.length + 1
            }
            // The rest must be empty or chords — "Chorus of angels" is a lyric.
            val restTokens = restWords.map { token(it.text) to it }
            val restIsChords = restTokens.all { it.first !is Token.Word }
            val restHasChord = restTokens.any { it.first is Token.Chord }
            if (restIsChords && (!short || number != null || hasColon || restHasChord)) {
                val (kind, label) = sectionKind(name)
                val chords = restTokens.mapNotNull { (t, w) -> (t as? Token.Chord)?.let { PlacedChord(it.name, w.box.minX, it.note) } }
                return row.copy(kind = RowKind.Header(kind, label, number, repeatNote, chords))
            }
        }

        var tokens = row.words.map { token(it.text) to it }
        // A row that already has a couple of clean chords is a chord row; read the rest of it as one, with bar lines
        // the reader turned into letters ("(|C" → "(IC", "|)" → "1)") taken as bar lines.
        if (tokens.count { it.first is Token.Chord } >= 2) {
            tokens = tokens.map { (t, w) -> if (t is Token.Word) barToken(w.text) to w else t to w }
        }
        val counted = tokens.filter { it.first !is Token.Bar }
        val chords = tokens.mapNotNull { (t, w) -> (t as? Token.Chord)?.let { PlacedChord(it.name, w.box.minX, it.note) } }
        val kind = when {
            chords.isNotEmpty() && chords.size.toDouble() >= 0.75 * counted.size.toDouble() -> RowKind.Chords(chords)
            graphemeCount(text) < 48 && metaRegex.containsMatchIn(text) -> RowKind.Meta
            else -> RowKind.Lyric
        }
        return row.copy(kind = kind)
    }

    // MARK: - Chord diagrams and strumming patterns

    private val diagramRegex = Regex("""[xXoO0-9○●•×]+""")
    private val repeatMarkRegex = Regex("""(?i)[x×][2-9]""")

    /** A mark from a chord diagram: string markers and fret numbers ("x", "o", "0", "23", "020", "x32010"). */
    fun isDiagramWord(text: String): Boolean = diagramRegex.matches(text) && !repeatMarkRegex.matches(text)

    private val arrows = setOf('↓', '↑', '⇩', '⇧', '⬇', '⬆')
    private val arrowStrings = setOf("🡓", "🡑")

    fun isArrowWord(text: String): Boolean {
        if (text.isEmpty()) return false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = String(Character.toChars(cp))
            if (!(s.length == 1 && s[0] in arrows) && s !in arrowStrings) return false
            i += Character.charCount(cp)
        }
        return true
    }

    /**
     * Things printed on a sheet that aren't the song itself:
     * - chord diagrams: rows of string markers and fret numbers ("x o X X 0 0", "23 020", "x32010"), and the row of
     *   chord names next to them (a legend, not chords to play);
     * - strumming patterns written as arrows, which become `Strum` rows — split off any credit sharing the line
     *   ("တေးရေး - ဝင့်နိုင်  ↓ ↓ ↓ ↑ ↓") and joined with a pattern continuing on the next line.
     */
    fun sheetFurniture(rows: List<Row>): List<Row> {
        fun isDiagram(row: Row): Boolean {
            val words = row.readWords.map { it.text }
            return words.isNotEmpty() && words.all(::isDiagramWord) && (words.size >= 3 || words.any { it.length >= 3 })
        }
        fun near(a: Row, b: Row): Boolean = abs(a.box.midY - b.box.midY) < max(a.box.height, b.box.height) * 2.5

        /**
         * A chord row at the top of the page whose chords all sit past the end of the line below can't be that line's
         * chords: it's the sheet's chord legend ("F Em Am Dm" top right, beside the title).
         */
        fun isLegend(i: Int): Boolean {
            if (i >= 4) return false
            val chords = (rows[i].kind as? RowKind.Chords)?.chords ?: return false
            if (i + 1 >= rows.size || rows[i + 1].kind !is RowKind.Lyric) return false
            // Above the line, not chords written at its end (those share its baseline).
            if (rows[i].box.maxY > rows[i + 1].box.minY + rows[i + 1].box.height * 0.3) return false
            val end = rows[i + 1].box.maxX
            return chords.all { it.x > end }
        }

        val out = ArrayList<Row>()
        for ((i, row) in rows.withIndex()) {
            if (isDiagram(row) || isLegend(i)) continue
            if (row.kind is RowKind.Chords &&
                ((i > 0 && isDiagram(rows[i - 1]) && near(rows[i - 1], row)) || (i + 1 < rows.size && isDiagram(rows[i + 1]) && near(row, rows[i + 1])))
            ) continue
            val arrowWords = row.words.filter { isArrowWord(it.text) }
            if (arrowWords.isEmpty() || !(arrowWords.size == row.words.size || arrowWords.size >= 3)) {
                out.add(row)
                continue
            }
            val rest = row.words.filter { !isArrowWord(it.text) }
            if (rest.isNotEmpty()) out.add(classify(Row(rest, row.confidence)))
            val pattern = arrowWords.joinToString(" ") { it.text }
            val last = out.lastOrNull()
            val before = (last?.kind as? RowKind.Strum)?.pattern
            if (last != null && before != null && near(last, row)) {
                out[out.size - 1] = last.copy(words = last.words + arrowWords, kind = RowKind.Strum("$before $pattern"))
            } else {
                out.add(Row(arrowWords, row.confidence, RowKind.Strum(pattern)))
            }
        }
        return out
    }

    // MARK: - Title and credits

    /**
     * Words that introduce a credit, singer first. Myanmar: တေးဆို (singer), ဆိုသူ, တေးသံရှင်, သီဆို, အဆို;
     * တေးရေး (songwriter), ရေးစပ်, ရေးသူ, သံစဉ် (melody), စာသား (lyrics), ဂီတ/တီးလုံး (music).
     */
    private val singerWords = listOf("တေးဆိုရှင်", "တေးဆို", "ဆိုသူ", "တေးသံရှင်", "သီဆို", "အဆို",
        "sung by", "singer", "vocals", "vocal", "artist", "by")
    private val writerWords = listOf("တေးရေးဆရာ", "တေးရေး", "ရေးစပ်", "ရေးသူ", "သံစဉ်", "စာသား", "ဂီတ", "တီးလုံး",
        "written by", "words by", "lyrics", "lyric", "music", "composed by", "composer",
        "arranged by", "arr.", "chords by", "original", "cover")

    data class Credit(val singer: String?, val writer: String?)

    private class Hit(val range: IntRange, val singer: Boolean)

    /** A credit line ("by The Wanderers", "တေးရေး-ကန် တေးဆို-မျိုးကြီး", "တေးရေး၊တေးဆို-မာရဂ္ဂ") and the names in it. */
    fun credit(text: String): Credit? {
        val lower = text.lowercase()
        // Lowercasing can change the length for a few letters; then read names from the lowercased text.
        val source = if (lower.length == text.length) text else lower
        // Every keyword occurrence, earliest first (longer keywords win where they overlap).
        val hits = ArrayList<Hit>()
        for ((words, singer) in listOf(singerWords to true, writerWords to false)) {
            for (w in words.sortedByDescending { it.length }) {
                var from = 0
                while (true) {
                    val at = lower.indexOf(w, from)
                    if (at < 0) break
                    val r = at until at + w.length
                    // Latin keywords must be whole words ("by", not "baby").
                    val isLatin = w.first().code < 128
                    val before = if (at == 0) null else lower[at - 1]
                    val after = if (r.last + 1 >= lower.length) null else lower[r.last + 1]
                    // Myanmar ones must be followed by a separator or space ("တေးဆို-…", "ဆိုသူ …"), so a title that
                    // merely contains one ("ဂီတချစ်သူ") isn't a credit.
                    val whole = if (isLatin) {
                        (before?.let { !it.isLetter() } ?: true) && (after?.let { !it.isLetter() } ?: true)
                    } else {
                        after?.let { it in " -–—:=၊။/|,()" || it.isWhitespace() } ?: true
                    }
                    if (whole && hits.none { it.range.overlaps(r) }) hits.add(Hit(r, singer))
                    from = r.last + 1
                }
            }
        }
        if (hits.isEmpty()) return null
        // An English keyword only makes a credit at the start of the line ("by …", "Lyrics: …"), not mid-lyric.
        hits.sortBy { it.range.first }
        val leadingLatin = lower.substring(0, hits[0].range.first).trim { it == ' ' || it == '\t' }.isEmpty()
        if (hits.all { lower[it.range.first].code < 128 } && !leadingLatin) return null

        var singer: String? = null
        var writer: String? = null
        val separators = " -–—:=၊။/|,()[]"
        for ((i, hit) in hits.withIndex()) {
            val end = if (i + 1 < hits.size) hits[i + 1].range.first else source.length
            val name = source.substring(hit.range.last + 1, end).trim { it in separators || it.isWhitespace() }
            if (name.isEmpty()) continue
            if (hit.singer) singer = singer ?: name else writer = writer ?: name
        }
        return Credit(singer, writer)
    }

    private fun IntRange.overlaps(o: IntRange): Boolean = first <= o.last && o.first <= last

    private val titleLabelRegex = Regex("""(?i)^(?:title|song|ခေါင်းစဉ်|သီချင်းအမည်|သီချင်း)\s*[:\-–]\s*""")
    private val timeSignatureRegex = Regex("""(?i)^\d{1,2}\s*/\s*\d{1,2}\s*(?:beat|time)?\s*[:\-–]?\s*""")

    /** A title as written: without quotes, a "Title:" / "ခေါင်းစဉ်:" label or a trailing full stop. */
    fun cleanTitle(text: String): String {
        var t = text.trim { it == ' ' || it == '\t' }
        titleLabelRegex.find(t)?.let { m -> t = t.substring(m.range.last + 1) }
        // A time signature written before the title ("6/8 beat Mary").
        timeSignatureRegex.find(t)?.let { m -> if (m.range.last + 1 < t.length) t = t.substring(m.range.last + 1) }
        t = t.trim { it in "\"“”'‘’「」『』«»*" || it.isWhitespace() }
        t = t.trim { it in ".။" }
        return t
    }

    private val metaWordRegex = Regex("""(?i)^(key|capo|tempo|bpm)\b[:=\-]?(.*)$""")
    private val metaValueRegex = Regex("""(?i)\d{2,3}\s*bpm|[:=\-]""")

    /** A key/capo/tempo row without its key/capo/tempo words ("ညီလေး   Key-G" → "ညီလေး"). */
    fun withoutMeta(row: Row): Row {
        val kept = ArrayList<ScanWord>()
        var skipValue = false
        for (w in row.words) {
            if (skipValue) { skipValue = false; continue }
            val m = metaWordRegex.find(w.text)
            if (m != null) {
                // "Key" / "Key:" alone: its value is the next word.
                if (m.groupValues[2].isEmpty()) skipValue = true
                continue
            }
            if (metaValueRegex.matches(w.text)) continue
            kept.add(w)
        }
        return row.copy(words = kept, kind = RowKind.Lyric)
    }

    // MARK: - Chords and labels inside Myanmar lines

    /**
     * A section label written at the start of a lyric line: "V1", "V1,", "Verse 2", "Ch", "C1", "R", "Bridge:", "(Pre)".
     * One-letter forms need a number ("C1", "B2") — bare C or B is a chord.
     */
    private val inlineLabelRegex = Regex(
        """^[\[(]?(verse|v|chorus|cho|ch|c|refrain|ref|r|pre[- ]?chorus|pre|bridge|b|intro|int|outro|solo|music)\.?(\d+)?[\])]?[:.,\-–]?$""",
        RegexOption.IGNORE_CASE,
    )

    data class InlineLabel(val kind: SectionKind, val label: String?, val number: Int?)

    fun inlineLabel(text: String): InlineLabel? {
        val m = inlineLabelRegex.matchEntire(text) ?: return null
        val name = m.groupValues[1].lowercase()
        val number = m.groups[2]?.value?.toIntOrNull()
        if (name in listOf("v", "c", "r", "b") && number == null) return null
        val canonical = mapOf("c" to "chorus", "r" to "refrain", "ref" to "refrain", "b" to "bridge", "int" to "intro", "pre" to "pre-chorus")[name] ?: name
        val (kind, label) = sectionKind(canonical)
        return InlineLabel(kind, label, number)
    }

    /**
     * Myanmar sheets often put chords on the lyric line itself — at the end ("…တာတွေ  Bm"), or merged into it when
     * the chord row sits tight above tall Myanmar letters — and a section label at its start ("V1 မသေချာ…").
     * Myanmar lyrics never contain Latin chord names, so in a Myanmar line those words are chords: they're lifted
     * into a chord row over the line (joining the chord row just above, if there is one), and a leading label starts
     * its section. English lines are left alone ("A", "Am" can be words).
     */
    fun separateInline(rows: List<Row>): List<Row> {
        val out = ArrayList<Row>()
        for (row in rows) {
            // "Ch  G  Em" over a lyric line: the chords are that line's, not instrumental bars (only an intro, solo
            // or other music section plays its chords as bars).
            val h = row.kind as? RowKind.Header
            if (h != null && h.kind != SectionKind.Music && h.chords.isNotEmpty()) {
                val chordWords = row.words.filter { w -> h.chords.any { it.x == w.box.minX } }
                out.add(Row(row.words.filter { w -> chordWords.none { it.box == w.box } }, row.confidence, h.copy(chords = emptyList())))
                out.add(Row(chordWords, row.confidence, RowKind.Chords(h.chords)))
                continue
            }
            if (row.kind !is RowKind.Lyric || !isMyanmar(row.text)) { out.add(row); continue }
            var words = row.words
            var header: Row? = null
            val first = words.firstOrNull()
            if (first != null && !isMyanmar(first.text) && words.size > 1) {
                inlineLabel(first.text)?.let { label ->
                    header = Row(listOf(first), row.confidence, RowKind.Header(label.kind, label.label, label.number, null, emptyList()))
                    words = words.drop(1)
                }
            }
            val chordWords = words.filter { w -> !isMyanmar(w.text) && token(w.text) is Token.Chord }
            val lyricWords = words.filter { w -> chordWords.none { it.box == w.box && it.text == w.text } }
            if (!(header != null || chordWords.isNotEmpty()) || lyricWords.none { isMyanmar(it.text) }) {
                out.add(row)
                continue
            }
            val lyric = Row(lyricWords, row.confidence, RowKind.Lyric)
            // The chord row right above (if any) belongs to this line too.
            val aboveIndex: Int? = out.lastOrNull()?.let { last ->
                if (last.kind is RowKind.Chords && lyric.box.minY - last.box.maxY < max(last.box.height, lyric.box.height) * 1.6) out.size - 1 else null
            }
            header?.let { if (aboveIndex != null) out.add(aboveIndex, it) else out.add(it) }
            if (chordWords.isNotEmpty()) {
                val placed = chordWords.mapNotNull { w -> (token(w.text) as? Token.Chord)?.let { PlacedChord(it.name, w.box.minX, it.note) } }
                val i = aboveIndex?.let { if (header == null) it else it + 1 }
                val existing = i?.let { (out[it].kind as? RowKind.Chords)?.chords }
                if (i != null && existing != null) {
                    out[i] = out[i].copy(words = out[i].words + chordWords, kind = RowKind.Chords((existing + placed).sortedBy { it.x }))
                } else {
                    // Its own chord row, at the lyric's height (so it reads as directly above the line).
                    out.add(Row(chordWords, row.confidence, RowKind.Chords(placed)))
                }
            }
            out.add(lyric)
        }
        return out
    }

    private fun sectionKind(name: String): Pair<SectionKind, String?> = when (name) {
        "intro" -> SectionKind.Music to "INTRO"
        "verse", "v" -> SectionKind.Verse to null
        "chorus", "ch", "cho", "refrain", "hook" -> SectionKind.Chorus to "CHORUS"
        "bridge" -> SectionKind.Bridge to "BRIDGE"
        "outro", "ending", "coda" -> SectionKind.Music to "OUTRO"
        "tag" -> SectionKind.Music to "TAG"
        else -> if (name.startsWith("pre")) SectionKind.Bridge to "PRE-CHORUS" else SectionKind.Music to (if (name == "solo") "SOLO" else "MUSIC")
    }

    // MARK: - Lyrics

    fun isMyanmar(s: String): Boolean = s.any { it.code in 0x1000..0x109F }

    private val wordLikeRegex = Regex("""["“(]?(?:[A-Za-z][a-z]*(?:['’][a-z]+)?|[A-Z]{2,})[,.!?;:"”)]*|[0-9]+[,.]?|&|-|—""")

    /** An English-looking word ("Morning", "shine,", "don’t", "2"), as opposed to OCR noise like "ccosg:l" or "8/9". */
    fun isWordLike(s: String): Boolean = wordLikeRegex.matches(s)

    /** Whether OCR gave us real words (ML Kit / Vision turn Myanmar script into low-confidence Latin noise). */
    fun isReadable(row: Row, readsMyanmar: Boolean): Boolean {
        // Mostly placeholders: the line is in a script we couldn't read.
        if (row.readWords.size * 2 <= row.words.size) return false
        val chars = row.readText.filter { !it.isWhitespace() }
        if (chars.isEmpty()) return false
        if (isMyanmar(row.text)) return readsMyanmar
        if (row.confidence < 0.45f) return false
        // Vision's reading of Myanmar script looks like "ccosg:l :co:ces cuçuccio" — letters, but not words.
        val words = row.readWords.map { it.text }
        val wordLike = words.count(::isWordLike)
        val vowels = chars.count { it in "aeiouyAEIOUY" }
        return wordLike.toDouble() >= 0.75 * words.size.toDouble() && vowels * 6 >= chars.length
    }

    /**
     * The text as user-perceived characters (extended grapheme clusters, as Swift's `Character`): a letter with its
     * combining marks is one item, so a Myanmar killed final like "က်" is never split. The Myanmar vowel signs
     * ာ, ါ, း (and a few more) stand alone, as in Unicode's grapheme rules.
     */
    fun graphemes(text: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (i > start && !extendsCluster(cp)) { out.add(text.substring(start, i)); start = i }
            i += width
        }
        if (start < text.length) out.add(text.substring(start))
        return out
    }

    fun graphemeCount(text: String): Int = graphemes(text).size

    /** Myanmar spacing marks that Unicode's grapheme rules do not attach to the letter before them. */
    private val standaloneSpacingMarks = setOf(0x102B, 0x102C, 0x1038, 0x1062, 0x1063, 0x1064, 0x1067, 0x1068, 0x1069, 0x106A, 0x106B, 0x106C, 0x106D,
        0x1083, 0x1087, 0x1088, 0x1089, 0x108A, 0x108B, 0x108C, 0x108F, 0x109A, 0x109B, 0x109C)

    private fun extendsCluster(cp: Int): Boolean {
        if (cp == 0x200C || cp == 0x200D) return true
        return when (Character.getType(cp)) {
            Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
            Character.COMBINING_SPACING_MARK.toInt() -> cp !in standaloneSpacingMarks
            else -> false
        }
    }

    /**
     * Indexes (in characters, see [graphemes]) where a Myanmar syllable starts: a consonant, independent vowel or
     * digit that isn't a killed final (with ်) and isn't stacked under the previous letter (after ္).
     */
    fun myanmarSyllableStarts(chars: List<String>): List<Int> = chars.indices.filter { i ->
        val first = chars[i].codePointAt(0)
        if (!(first in 0x1000..0x102A || first in 0x1040..0x1049 || first == 0x103F)) return@filter false
        if (chars[i].any { it.code == 0x103A }) return@filter false
        if (i > 0 && chars[i - 1].lastOrNull()?.code == 0x1039) return@filter false
        true
    }

    /** [myanmarSyllableStarts] of the text's characters ([graphemes]). */
    fun myanmarSyllableStarts(text: String): List<Int> = myanmarSyllableStarts(graphemes(text))

    /** Puts each chord on the syllable under it: "[C]lyric wo[G/B]rds". */
    fun merge(chords: List<PlacedChord>, lyric: Row): String {
        val words = lyric.readWords
        val chars = ArrayList<String>()
        val starts = ArrayList<Int>()
        for ((k, w) in words.withIndex()) {
            if (k > 0) chars.add(" ")
            starts.add(chars.size)
            chars.addAll(graphemes(w.text))
        }
        class Insert(val offset: Int, val order: Int, val text: String)
        val inserts = ArrayList<Insert>()
        for ((n, c) in chords.withIndex()) {
            val k = words.indexOfFirst { c.x < it.box.maxX }
            if (k >= 0) {
                val w = words[k]
                val frac = (c.x - w.box.minX) / max(w.box.width, 0.0001)
                val count = graphemeCount(w.text)
                var within = if (frac <= 0.34) 0 else min(count - 1, (frac * count).roundToInt())
                // Myanmar: never split a syllable (e.g. the killed final in စက်), only start one.
                if (within > 0 && isMyanmar(w.text)) {
                    val syllables = myanmarSyllableStarts(graphemes(w.text))
                    within = syllables.minByOrNull { abs(it - within) } ?: 0
                }
                inserts.add(Insert(starts[k] + within, n, "[${c.name}]"))
            } else {
                inserts.add(Insert(chars.size, n, " [${c.name}]"))
            }
        }
        for (ins in inserts.sortedWith(compareByDescending<Insert> { it.offset }.thenByDescending { it.order })) {
            chars.add(ins.offset, ins.text)
        }
        return chars.joinToString("").trim { it == ' ' || it == '\t' }
    }

    /** Width, in characters, that chord-only lines are laid out across. */
    const val columns = 48

    /**
     * "[C]          [G/B]      " — spaces keep each chord's place across the line, and trailing spaces mark where
     * the unread lyric line ended on the page, so typed lyrics can be lined up later.
     */
    fun chordsOnly(chords: List<PlacedChord>, margin: Double, width: Double, lyricEnd: Double? = null): String {
        fun column(x: Double): Int = (((x - margin) / max(width, 0.0001)) * columns).toInt()
        val out = StringBuilder()
        var col = 0
        for (c in chords.sortedBy { it.x }) {
            val target = max(col, column(c.x))
            out.append(" ".repeat(target - col)).append("[${c.name}]")
            col = target
        }
        if (lyricEnd != null && column(lyricEnd) > col) out.append(" ".repeat(column(lyricEnd) - col))
        return out.toString()
    }

    /** The words of a "[C]lyric [G]line" without its chords. */
    fun words(line: String): String =
        ChordLine.parse(line).joinToString("") { it.text + (if (it.trailingSpace) " " else "") }.trim { it == ' ' || it == '\t' }

    private class ChordAt(val col: Int, val name: String)
    private class WordAt(val start: Int, val word: String)
    private class Anchor(val old: Int, val new: Int)

    /** Each chord's column in the plain text, and the plain text itself (as characters). */
    private fun chordColumns(line: String): Pair<List<ChordAt>, List<String>> {
        val chords = ArrayList<ChordAt>()
        val plain = ArrayList<String>()
        var i = 0
        while (i < line.length) {
            val close = if (line[i] == '[') line.indexOf(']', i) else -1
            if (close >= 0) {
                chords.add(ChordAt(plain.size, line.substring(i + 1, close)))
                i = close + 1
            } else {
                val cp = line.codePointAt(i)
                val width = Character.charCount(cp)
                val piece = line.substring(i, i + width)
                if (plain.isNotEmpty() && extendsCluster(cp)) plain[plain.size - 1] = plain.last() + piece else plain.add(piece)
                i += width
            }
        }
        return chords to plain
    }

    private fun wordsOf(text: List<String>): List<WordAt> =
        text.indices.filter { text[it] != " " && (it == 0 || text[it - 1] == " ") }.map { s ->
            val end = (s until text.size).firstOrNull { text[it] == " " } ?: text.size
            WordAt(s, text.subList(s, end).joinToString(""))
        }

    /**
     * The line with its words replaced by `newWords`, each chord kept with its word. The old and new words are
     * lined up (longest common subsequence): a chord on a word that's still there stays on it, at the same place in
     * the word; a chord on a changed word goes to the matching spot between the unchanged words around it (a typo
     * fixed keeps its chord exactly); a chord after the last word stays at the end.
     */
    fun reword(line: String, newWords: String): String {
        val (chords, plain) = chordColumns(line)
        val new = graphemes(newWords.trim { it == ' ' || it == '\t' })
        if (chords.isEmpty()) return new.joinToString("")
        if (new.isEmpty()) return chords.joinToString(" ") { "[${it.name}]" }

        val old = wordsOf(plain)
        val now = wordsOf(new)
        val anchors = commonWords(old.map { it.word }, now.map { it.word })
        val oldEnd = plain.indexOfLast { it != " " }.let { if (it < 0) 0 else it + 1 }

        class Placed(val at: Int, val name: String)
        val placed = ArrayList<Placed>()
        for (c in chords) {
            var at = new.size                                       // after the last word
            if (c.col < oldEnd && now.isNotEmpty()) {
                val w = old.indexOfLast { it.start <= c.col }.let { if (it < 0) 0 else it }
                val inWord = c.col - (if (old.isEmpty()) 0 else old[w].start)
                val prev = anchors.lastOrNull { it.old <= w }
                val next = anchors.firstOrNull { it.old > w }
                if (prev != null && prev.old == w) {
                    // The chord's word is still there.
                    val word = now[prev.new]
                    at = word.start + min(inWord, max(0, graphemeCount(word.word) - 1))
                } else {
                    // Between the unchanged words around it: the old words there, and what replaced them.
                    val lo = prev?.let { it.old to it.new } ?: (-1 to -1)
                    val hi = next?.let { it.old to it.new } ?: (old.size to now.size)
                    val oldGap = hi.first - lo.first - 1
                    val newGap = hi.second - lo.second - 1
                    if (newGap <= 0) {
                        // Its words were deleted: the next word that's left, or the end of the line.
                        at = next?.let { now[it.new].start } ?: new.size
                    } else {
                        val position = (w - lo.first - 1).toDouble() / max(1, oldGap - 1).toDouble()
                        val target = lo.second + 1 + (position * (newGap - 1).toDouble()).roundToInt()
                        val word = now[min(max(0, target), now.size - 1)]
                        // A word swapped for another (the gaps match) keeps its place in the word.
                        at = word.start + (if (oldGap == newGap) min(inWord, max(0, graphemeCount(word.word) - 1)) else 0)
                    }
                }
            }
            placed.lastOrNull()?.let { if (at < it.at) at = it.at }  // keep the chords in order
            placed.add(Placed(at, c.name))
        }
        val out = ArrayList(new)
        for (p in placed.asReversed()) {
            out.add(min(p.at, out.size), if (p.at >= new.size) " [${p.name}]" else "[${p.name}]")
        }
        return out.joinToString("")
    }

    /** Pairs of indexes (old, new) of the words two lists have in common, in order (longest common subsequence). */
    private fun commonWords(a: List<String>, b: List<String>): List<Anchor> {
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val len = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                len[i][j] = if (a[i] == b[j]) len[i + 1][j + 1] + 1 else max(len[i + 1][j], len[i][j + 1])
            }
        }
        val pairs = ArrayList<Anchor>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j]) { pairs.add(Anchor(i, j)); i += 1; j += 1 } else if (len[i + 1][j] >= len[i][j + 1]) i += 1 else j += 1
        }
        return pairs
    }

    /** The chords of `line` placed onto `lyric` (a line typed from scratch), each on the nearest word start. */
    fun placeChords(line: String, lyric: String): String {
        val (chords, plain) = chordColumns(line)
        val col = plain.size
        val text = graphemes(lyric.trim { it == ' ' || it == '\t' })
        if (text.isEmpty()) return line
        val wordStarts = text.indices.filter { it == 0 || (text[it - 1] == " " && text[it] != " ") }
        // Where the lyric ended on the page (trailing spaces), or else one chord-gap past the last chord.
        val last = (chords.lastOrNull()?.col ?: 0).toDouble()
        val extent = when {
            line.endsWith(" ") -> col.toDouble()
            chords.size > 1 -> last + last / (chords.size - 1).toDouble()
            else -> last * 2
        }
        val offsets = ArrayList<Int>()
        for (c in chords) {
            val target = ((if (extent > 0) c.col.toDouble() / extent else 0.0) * text.size.toDouble()).toInt()
            var best = wordStarts.minByOrNull { abs(it - target) } ?: 0
            offsets.lastOrNull()?.let { prev -> if (best <= prev) best = min(text.size, max(prev + 1, target)) }
            offsets.add(best)
        }
        val out = ArrayList(text)
        for (k in chords.indices.reversed()) {
            val at = min(offsets[k], out.size)
            out.add(at, if (at == text.size) " [${chords[k].name}]" else "[${chords[k].name}]")
        }
        return out.joinToString("")
    }

    // MARK: - Key

    /** Best-fitting major key (or its relative minor when the song starts and ends there). */
    fun guessKey(chords: List<String>): String {
        class Parts(val root: Int, val minor: Boolean)
        fun parts(c: String): Parts? {
            val first = c.firstOrNull() ?: return null
            var root = first.toString()
            val rest = c.drop(1)
            rest.firstOrNull()?.let { acc -> if (acc == '#' || acc == 'b') root += acc }
            val i = ChordTheory.index[root] ?: return null
            val quality = c.drop(root.length).split("/").firstOrNull() ?: ""
            return Parts(i, quality.startsWith("m") && !quality.startsWith("maj"))
        }
        val parsed = chords.mapNotNull(::parts)
        val first = parsed.firstOrNull() ?: return "C"
        val last = parsed.last()
        val degrees = listOf(0 to false, 2 to true, 4 to true, 5 to false, 7 to false, 9 to true)
        var bestTonic = 0
        var bestScore = -1
        for (tonic in 0 until 12) {
            var score = parsed.count { p -> degrees.any { (d, minor) -> (d + tonic) % 12 == p.root && minor == p.minor } } * 2
            if (first.root == tonic && !first.minor) score += 3
            if (last.root == tonic && !last.minor) score += 3
            if (score > bestScore) { bestTonic = tonic; bestScore = score }
        }
        val relMinor = (bestTonic + 9) % 12
        if (first.minor && first.root == relMinor && last.root == relMinor) return ChordTheory.names[relMinor] + "m"
        return ChordTheory.names[bestTonic]
    }

    // MARK: - Building

    private val keyRegex = Regex("""(?i)key\s*(?:of)?\s*[:=\-]?\s*([A-Ga-g][#b]?m?)\b""")
    private val capoRegex = Regex("""(?i)capo\s*[:=\-]?\s*(\d{1,2})""")
    private val tempoRegex = Regex("""(?i)(\d{2,3})\s*bpm|tempo\s*[:=\-]?\s*(\d{2,3})""")
    private val titleSingerRegex = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

    private class Builder(val readsMyanmar: Boolean) {
        /** Left edge and width of the text on the page, for laying out chord-only lines. */
        var margin = 0.0
        var width = 1.0
        var title = ""
        var artist = ""
        var key: String? = null
        var tempo: Int? = null
        var capo: Int? = null
        val sections = ArrayList<SongSection>()
        val sectionBoxes = ArrayList<Box?>()
        val needs = ArrayList<ScanLineRef>()
        var verseCount = 0
        val lowConfidence = ArrayList<String>()
        val notes = LinkedHashMap<String, String>()
        var readableLyrics = 0
        var sawMyanmar = false
        /** A strumming pattern for the whole song (written with the title), and one waiting for the next section. */
        var songStrum: String? = null
        var pendingStrum: String? = null

        fun readMeta(row: Row) {
            val text = row.text
            if (key == null) keyRegex.find(text)?.let { m ->
                val k = m.groupValues[1]
                key = k.take(1).uppercase() + k.drop(1)
            }
            if (capo == null) capoRegex.find(text)?.let { m -> capo = m.groupValues[1].toIntOrNull() }
            if (tempo == null) tempoRegex.find(text)?.let { m ->
                tempo = (m.groups[1]?.value ?: m.groups[2]?.value ?: "").toIntOrNull()
            }
        }

        /** The lines above the first chords: title, credits ("by …", "တေးရေး-… တေးဆို-…") and key/capo/tempo. */
        fun readPreamble(rows: List<Row>) {
            val candidates = ArrayList<Row>()
            var writer: String? = null
            for (row in rows) {
                val kind = row.kind
                if (kind is RowKind.Strum) {
                    songStrum = kind.pattern
                    continue
                }
                if (kind is RowKind.Meta) {
                    readMeta(row)
                    // "ညီလေး   Key-G": what's left after the key/capo/tempo is still a title candidate.
                    val rest = withoutMeta(row)
                    if (rest.words.isNotEmpty()) candidates.add(rest)
                    continue
                }
                val credit = credit(row.readText)
                if (credit != null) {
                    // The singer is the artist; a songwriter only if no singer turns up.
                    if (artist.isEmpty()) credit.singer?.let { artist = it }
                    writer = writer ?: credit.writer
                    if (isMyanmar(row.text)) sawMyanmar = true
                    continue
                }
                // Fret numbers from a chord diagram printed beside the title ("…ရင်  23 020").
                var r = row
                if (r.words.any { !isDiagramWord(it.text) }) r = r.copy(words = r.words.filterNot { isDiagramWord(it.text) })
                candidates.add(r)
            }
            try {
                val readable = candidates.filter { isReadable(it, readsMyanmar) && cleanTitle(it.readText).isNotEmpty() }
                if (candidates.any { isMyanmar(it.text) }) sawMyanmar = true
                val best = readable.maxByOrNull { titleScore(it, readable) } ?: return

                var text = cleanTitle(best.readText)
                // "ညီလေး - မျိုးကြီး": title, then the singer.
                if (artist.isEmpty()) titleSingerRegex.find(text)?.let { m ->
                    text = m.groupValues[1]
                    artist = m.groupValues[2]
                }
                title = text
                // A short readable line right under the title is usually the artist ("The Wanderers").
                if (artist.isEmpty()) {
                    val i = readable.indexOfFirst { it.box == best.box }
                    if (i >= 0 && i + 1 < readable.size) {
                        val next = readable[i + 1]
                        if (graphemeCount(next.readText) <= 40 && next.box.height <= best.box.height) artist = cleanTitle(next.readText)
                    }
                }
            } finally {
                if (artist.isEmpty()) writer?.let { artist = it }
            }
        }

        /**
         * How title-like a line is: mostly its type size, plus a little for being centred and near the top, minus
         * for being long like a lyric.
         */
        private fun titleScore(row: Row, rows: List<Row>): Double {
            val tallest = rows.maxOfOrNull { it.box.height } ?: 1.0
            var score = row.box.height / max(tallest, 0.0001)
            if (abs(row.box.midX - (margin + width / 2)) < width * 0.12) score += 0.15
            if (row.box == rows.firstOrNull()?.box) score += 0.1
            if (graphemeCount(cleanTitle(row.readText)) > 32) score -= 0.3
            return score
        }

        private fun start(kind: SectionKind, label: String?, number: Int? = null, note: String? = null) {
            var l = label ?: ""
            if (kind == SectionKind.Verse) {
                verseCount = number ?: (verseCount + 1)
                l = "VERSE $verseCount"
            }
            sections.add(SongSection(kind = kind, label = l, note = note, strumPattern = pendingStrum))
            pendingStrum = null
            sectionBoxes.add(null)
        }

        /** The section new lyric lines go into. */
        private fun lyricSection() {
            val last = sections.lastOrNull()
            if (last == null || last.kind == SectionKind.Music) start(SectionKind.Verse, null)
        }

        private fun track(row: Row) {
            if (sectionBoxes.isEmpty()) return
            val i = sectionBoxes.size - 1
            sectionBoxes[i] = sectionBoxes[i]?.union(row.box) ?: row.box
        }

        private fun note(chords: List<PlacedChord>, confidence: Float) {
            for (c in chords) {
                c.note?.let { notes[c.name] = it }
                if ((c.note != null || confidence < 0.5f) && c.name !in lowConfidence) lowConfidence.add(c.name)
            }
        }

        private fun update(transform: (SongSection) -> SongSection) {
            sections[sections.size - 1] = transform(sections.last())
        }

        private fun addLine(text: String, missingLyrics: Boolean) {
            update { it.copy(lines = it.lines + text) }
            if (missingLyrics) needs.add(ScanLineRef(sections.last().id, sections.last().lines.size - 1))
        }

        fun readBody(rows: List<Row>) {
            var i = 0
            while (i < rows.size) {
                val row = rows[i]
                val next = if (i + 1 < rows.size) rows[i + 1] else null
                val gapBelow = next?.let { it.box.minY - row.box.maxY } ?: Double.POSITIVE_INFINITY

                when (val kind = row.kind) {
                    is RowKind.Header -> {
                        start(kind.kind, kind.label, kind.number, kind.note)
                        track(row)
                        if (kind.chords.isNotEmpty()) {
                            update { it.copy(bars = it.bars + kind.chords.map { c -> c.name }) }
                            note(kind.chords, row.confidence)
                        }
                    }

                    is RowKind.Chords -> {
                        val chords = kind.chords
                        note(chords, row.confidence)
                        // Chords in brackets or between bar lines ("( | C | Am | Dm | G | )") are played on their own,
                        // not over the line below.
                        val text = row.text.trim { it == ' ' || it == '\t' }
                        val instrumental = (text.startsWith("(") && text.endsWith(")")) || text.contains("|")
                        val lyricBelow: Row? = if (!instrumental && next != null && next.kind is RowKind.Lyric &&
                            gapBelow < max(row.box.height, next.box.height) * 1.6) next else null
                        if (lyricBelow != null) {
                            lyricSection()
                            track(row); track(lyricBelow)
                            if (isMyanmar(lyricBelow.text)) sawMyanmar = true
                            if (isReadable(lyricBelow, readsMyanmar)) {
                                readableLyrics += 1
                                addLine(merge(chords, lyricBelow), missingLyrics = false)
                            } else {
                                addLine(chordsOnly(chords, margin, width, lyricEnd = lyricBelow.box.maxX), missingLyrics = true)
                            }
                            i += 2
                            continue
                        }
                        // No lyric right below. A wide gap means OCR skipped a line it couldn't read.
                        val skippedLine = !readsMyanmar && gapBelow > row.box.height * 1.2 && gapBelow != Double.POSITIVE_INFINITY
                        if (sections.isEmpty()) {
                            if (skippedLine) start(SectionKind.Verse, null) else start(SectionKind.Music, "INTRO")
                        }
                        track(row)
                        if (sections.last().kind == SectionKind.Music && !skippedLine) {
                            update { it.copy(bars = it.bars + chords.map { c -> c.name }) }
                        } else {
                            lyricSection()
                            addLine(chordsOnly(chords, margin, width), missingLyrics = !readsMyanmar)
                        }
                    }

                    is RowKind.Lyric -> {
                        // A thin unread strip right under the previous line is that line's lower marks, not a line.
                        val readable = isReadable(row, readsMyanmar)
                        val gapAbove = if (i > 0) row.box.minY - rows[i - 1].box.maxY else Double.POSITIVE_INFINITY
                        if (!readable && i > 0 && gapAbove < row.box.height * 0.3 && row.box.height < rows[i - 1].box.height * 0.7) {
                            track(row)
                        } else if (!readable && gapAbove > row.box.height * 2.5 && gapBelow > row.box.height * 2.5) {
                            // Unread ink with nothing near it (the page edge, a smudge) isn't a lyric line.
                        } else {
                            lyricSection()
                            track(row)
                            if (isMyanmar(row.text)) sawMyanmar = true
                            if (readable) {
                                readableLyrics += 1
                                addLine(row.readText, missingLyrics = false)
                            } else {
                                addLine("", missingLyrics = true)
                            }
                        }
                    }

                    is RowKind.Meta -> readMeta(row)

                    is RowKind.Strum -> {
                        // Right under a section's heading it's that section's pattern; before any section it's the
                        // song's; after a section's lines, it's for the next one.
                        val last = sections.lastOrNull()
                        if (last != null && last.lines.isEmpty() && last.bars.isEmpty()) {
                            update { it.copy(strumPattern = kind.pattern) }
                        } else if (sections.isEmpty()) {
                            songStrum = songStrum ?: kind.pattern
                        } else {
                            pendingStrum = kind.pattern
                        }
                    }
                }
                i += 1
            }
        }

        fun result(): ParsedSheet {
            var kept = sections.filter { it.lines.isNotEmpty() || it.bars.isNotEmpty() }
            songStrum?.let { strum ->
                kept = kept.map { if (it.strumPattern == null && it.kind != SectionKind.Music) it.copy(strumPattern = strum) else it }
            }
            kept = kept.map {
                if (it.kind == SectionKind.Music && it.note == null && it.bars.isNotEmpty()) it.copy(note = "${it.bars.size} bars") else it
            }
            val keptIDs = kept.map { it.id }.toSet()
            val draft = Song(id = UUID.randomUUID().toString(), title = title, artist = artist, key = "C", tempo = tempo ?: 80, sections = kept)
            val song = draft.copy(
                key = key ?: keyGuess(draft),
                isMyanmar = sawMyanmar || (needs.isNotEmpty() && needs.size >= readableLyrics),
                myNote = capo?.takeIf { it > 0 }?.let { "Capo $it" },
            )

            val regions = sections.zip(sectionBoxes).mapNotNull { (s, box) ->
                if (s.id !in keptIDs || box == null || box.minY >= 1) null
                else ScanRegion(s.kind, box.minX, box.minY, box.width, min(box.height, 1 - box.minY))
            }
            return ParsedSheet(song, lowConfidence.take(5), notes.toMap(), needs.filter { it.sectionID in keptIDs }, regions)
        }

        private fun keyGuess(song: Song): String =
            guessKey(song.sections.flatMap { it.bars + it.lines.flatMap { l -> ChordLine.chords(l) } })
    }
}
