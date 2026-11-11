package com.onezcodes.ochords.services

/** Transposition, capo and easy-chord rules from the "KEY · CAPO · EASY CHORDS" note. */
object ChordTheory {
    val names = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    val index: Map<String, Int> = mapOf("C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3, "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6,
        "G" to 7, "G#" to 8, "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11)

    /** Transpose a single note-rooted symbol ("Am7" by +2 → "Bm7"). */
    fun transposeRoot(chord: String, by: Int): String {
        val first = chord.firstOrNull() ?: return chord
        if (first !in "ABCDEFG") return chord
        var root = first.toString()
        var rest = chord.drop(1)
        val acc = rest.firstOrNull()
        if (acc == '#' || acc == 'b') { root += acc; rest = rest.drop(1) }
        val i = index[root] ?: return chord
        return names[((i + by) % 12 + 12) % 12] + rest
    }

    /** Transpose a full chord including slash bass ("G/B"). */
    fun transpose(chord: String, by: Int): String {
        if (by % 12 == 0) return chord
        val parts = chord.split("/").map { transposeRoot(it, by) }.toMutableList()
        // Spell the bass with sharps in sharp-side keys (E/G#, not E/Ab).
        val sharp = mapOf("Ab" to "G#", "Eb" to "D#", "Bb" to "A#")
        if (parts.size > 1 && listOf("E", "A", "B", "D", "F#", "C#").any { parts[0].startsWith(it) }) {
            sharp[parts[1]]?.let { parts[1] = it }
        }
        return parts.joinToString("/")
    }

    val easySwaps: Map<String, String> = mapOf("F" to "Fmaj7", "Bm" to "Bm7", "B" to "B7", "F#m" to "F#m7", "Bb" to "Bbmaj7", "C#m" to "C#m7", "Fm" to "Fm7")

    val openShapes: Set<String> = setOf("C", "D", "E", "G", "A", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7", "Fmaj7", "Bm7", "Cadd9", "Asus2", "Dsus4")

    fun isBarre(chord: String): Boolean = chord.split("/").first() !in openShapes

    data class Shown(val name: String, /** Set when the easy swap replaced the chord. */ val from: String? = null) {
        val isEasy: Boolean get() = from != null
    }

    /** Chord as the player's fingers see it: sounding shift minus capo, then the easy swap. */
    fun shown(chord: String, transpose: Int, capo: Int, easy: Boolean): Shown {
        val raw = transpose(chord, transpose - capo)
        if (!easy) return Shown(raw)
        val head = raw.split("/", limit = 2)[0]
        easySwaps[head]?.let { return Shown(it, raw) }
        return Shown(raw)
    }

    data class CapoOption(val capo: Int, val shapeKey: String, val shapes: List<String>, val barres: Int) { val id get() = capo }

    /** Capo 0–7 options whose shapes are C/G/D/A/E; ranked by barre count. */
    fun capoOptions(chords: List<String>, songKey: String, transpose: Int, current: Int): List<CapoOption> {
        val all = listOf(0, 1, 2, 3, 4, 5, 7).map { cp ->
            val shapes = chords.map { transpose(it, transpose - cp) }
            CapoOption(cp, transposeRoot(songKey, transpose - cp), shapes, shapes.count(::isBarre))
        }
        return all.filter { it.capo == 0 || it.capo == current || it.shapeKey in listOf("C", "G", "D", "A", "E") }.take(4)
    }

    /** "[C]lyric" lines transposed. */
    fun transposeLine(line: String, by: Int): String {
        val out = StringBuilder()
        var i = 0
        while (i < line.length) {
            val close = if (line[i] == '[') line.indexOf(']', i) else -1
            if (line[i] == '[' && close >= 0) {
                out.append('[').append(transpose(line.substring(i + 1, close), by)).append(']')
                i = close + 1
            } else { out.append(line[i]); i++ }
        }
        return out.toString()
    }
}
