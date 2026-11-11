package com.onezcodes.ochords.services

import com.onezcodes.ochords.model.Barre
import com.onezcodes.ochords.model.ChordDot
import com.onezcodes.ochords.model.ChordInfo
import com.onezcodes.ochords.model.ChordVoicing

/** Local chord shape library (from 7 · Chord finger guide). Strings: 0 = low E … 5 = high e. */
object ChordLibrary {
    private fun d(s: Int, f: Int, fi: Int) = ChordDot(s, f, fi)

    val all: Map<String, ChordInfo> = buildMap {
        fun add(name: String, full: String, v: List<ChordVoicing>) { put(name, ChordInfo(name, full, v)) }
        add("C", "C major", listOf(ChordVoicing(pattern = "x32010", dots = listOf(d(1, 3, 3), d(2, 2, 2), d(4, 1, 1)), tip = "Arch your ring finger so the open G string still rings.")))
        add("G/B", "G with B in the bass", listOf(ChordVoicing(pattern = "x20003", dots = listOf(d(1, 2, 2), d(5, 3, 3)), tip = "Walks the bass down C → G/B → Am. Let the tip of your middle finger touch the low E to mute it.")))
        add("Am", "A minor", listOf(ChordVoicing(pattern = "x02210", dots = listOf(d(2, 2, 2), d(3, 2, 3), d(4, 1, 1)), tip = "From C, keep index and middle where they are and move only your ring finger to the G string.")))
        add("F", "F major", listOf(
            ChordVoicing(label = "Easy", pattern = "xx3211", dots = listOf(d(2, 3, 3), d(3, 2, 2)), barre = Barre(1, 4, 5), tip = "Lay your index finger flat to press both the B and high e strings at fret 1."),
            ChordVoicing(label = "Full barre", pattern = "133211", dots = listOf(d(1, 3, 3), d(2, 3, 4), d(3, 2, 2)), barre = Barre(1, 0, 5), tip = "Press the barre with the bony side of your index finger, thumb low behind the neck."),
        ))
        add("Fmaj7", "F major 7", listOf(ChordVoicing(pattern = "xx3210", dots = listOf(d(2, 3, 3), d(3, 2, 2), d(4, 1, 1)), tip = "The easy stand-in for F — no barre, the high e rings open.")))
        add("G", "G major", listOf(ChordVoicing(pattern = "320003", dots = listOf(d(0, 3, 2), d(1, 2, 1), d(5, 3, 3)), tip = "Keep fingers arched so the open D, G and B strings ring clearly.")))
        add("Em", "E minor", listOf(ChordVoicing(pattern = "022000", dots = listOf(d(1, 2, 2), d(2, 2, 3)), tip = "The easiest chord to start with — strum all six strings.")))
        add("D", "D major", listOf(ChordVoicing(pattern = "xx0232", dots = listOf(d(3, 2, 1), d(4, 3, 3), d(5, 2, 2)), tip = "Your three fingers make a small triangle. Skip the two lowest strings.")))
        add("Dm", "D minor", listOf(ChordVoicing(pattern = "xx0231", dots = listOf(d(3, 2, 2), d(4, 3, 3), d(5, 1, 1)), tip = "Like D, but your index drops to fret 1 on the high e.")))
        add("A", "A major", listOf(ChordVoicing(pattern = "x02220", dots = listOf(d(2, 2, 1), d(3, 2, 2), d(4, 2, 3)), tip = "Three fingers side by side on fret 2 — angle them so they fit.")))
        add("E", "E major", listOf(ChordVoicing(pattern = "022100", dots = listOf(d(1, 2, 2), d(2, 2, 3), d(3, 1, 1)), tip = "Same shape as Am, moved one string lower.")))
        add("E7", "E dominant 7", listOf(ChordVoicing(pattern = "020100", dots = listOf(d(1, 2, 2), d(3, 1, 1)), tip = "E with the ring finger lifted.")))
        add("A7", "A dominant 7", listOf(ChordVoicing(pattern = "x02020", dots = listOf(d(2, 2, 2), d(4, 2, 3)), tip = "A with the G string left open.")))
        add("D7", "D dominant 7", listOf(ChordVoicing(pattern = "xx0212", dots = listOf(d(3, 2, 2), d(4, 1, 1), d(5, 2, 3)), tip = "A little upside-down triangle.")))
        add("G7", "G dominant 7", listOf(ChordVoicing(pattern = "320001", dots = listOf(d(0, 3, 3), d(1, 2, 2), d(5, 1, 1)), tip = "Index on the high e at fret 1.")))
        add("C7", "C dominant 7", listOf(ChordVoicing(pattern = "x32310", dots = listOf(d(1, 3, 3), d(2, 2, 2), d(3, 3, 4), d(4, 1, 1)), tip = "C with the pinky added on the G string.")))
        add("B7", "B dominant 7", listOf(ChordVoicing(pattern = "x21202", dots = listOf(d(1, 2, 2), d(2, 1, 1), d(3, 2, 3), d(5, 2, 4)), tip = "The easy stand-in for B.")))
        add("Bm7", "B minor 7", listOf(ChordVoicing(pattern = "x20202", dots = listOf(d(1, 2, 1), d(3, 2, 2), d(5, 2, 3)), tip = "The easy stand-in for Bm — no barre.")))
        add("Bm", "B minor", listOf(ChordVoicing(pattern = "x24432", dots = listOf(d(2, 4, 3), d(3, 4, 4), d(4, 3, 2)), barre = Barre(2, 1, 5), tip = "Barre fret 2 from the A string.")))
        add("B", "B major", listOf(ChordVoicing(pattern = "x24442", dots = listOf(d(2, 4, 2), d(3, 4, 3), d(4, 4, 4)), barre = Barre(2, 1, 5), tip = "A-shape barre at fret 2.")))
        add("F#m", "F-sharp minor", listOf(ChordVoicing(pattern = "244222", dots = listOf(d(1, 4, 3), d(2, 4, 4)), barre = Barre(2, 0, 5), tip = "Em shape barred at fret 2.")))
        add("C#m", "C-sharp minor", listOf(ChordVoicing(pattern = "x46654", dots = listOf(d(2, 6, 3), d(3, 6, 4), d(4, 5, 2)), barre = Barre(4, 1, 5), tip = "Am shape barred at fret 4.")))
        add("Bb", "B-flat major", listOf(ChordVoicing(pattern = "x13331", dots = listOf(d(2, 3, 2), d(3, 3, 3), d(4, 3, 4)), barre = Barre(1, 1, 5), tip = "A-shape barre at fret 1.")))
        add("Cadd9", "C add 9", listOf(ChordVoicing(pattern = "x32033", dots = listOf(d(1, 3, 2), d(2, 2, 1), d(4, 3, 3), d(5, 3, 4)), tip = "Pairs beautifully with G.")))
        add("Asus2", "A suspended 2", listOf(ChordVoicing(pattern = "x02200", dots = listOf(d(2, 2, 1), d(3, 2, 2)), tip = "A with the B string open.")))
        add("Dsus4", "D suspended 4", listOf(ChordVoicing(pattern = "xx0233", dots = listOf(d(3, 2, 1), d(4, 3, 3), d(5, 3, 4)), tip = "D with the pinky on the high e.")))
        add("D/F#", "D with F-sharp in the bass", listOf(ChordVoicing(pattern = "200232", dots = listOf(d(0, 2, 1), d(3, 2, 1), d(4, 3, 3), d(5, 2, 2)), tip = "Wrap the thumb over the top for the bass note.")))
    }

    /** Look up a chord; unknown chords fall back to their root triad when possible. */
    fun info(name: String): ChordInfo? = all[name] ?: all[name.split("/").first()]

    val common = listOf("Em", "D", "Dm", "A", "E", "G", "C", "Am")

    data class Degree(val degree: String, val chord: String)

    /** Diatonic chords per key (8 · Chords by key). */
    fun chordsInKey(key: String): List<Degree> {
        val majorSteps = listOf(0 to "", 2 to "m", 4 to "m", 5 to "", 7 to "", 9 to "m", 11 to "dim")
        val degrees = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
        return degrees.zip(majorSteps).map { (deg, step) -> Degree(deg, ChordTheory.transposeRoot(key, step.first) + step.second) }
    }
}
