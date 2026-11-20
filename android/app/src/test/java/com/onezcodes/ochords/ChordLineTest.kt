package com.onezcodes.ochords

import com.onezcodes.ochords.model.ChordLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordLineTest {

    @Test
    fun chordsExtractsBracketedNames() {
        assertEquals(listOf("C", "G/B"), ChordLine.chords("[C]lyric [G/B]more"))
        assertEquals(emptyList<String>(), ChordLine.chords("plain lyric"))
    }

    @Test
    fun parseAttachesChordToFollowingWord() {
        val segs = ChordLine.parse("[C]lyric [G/B]more")
        assertEquals(2, segs.size)
        assertEquals(ChordLine.Segment("C", "lyric", trailingSpace = true), segs[0])
        assertEquals(ChordLine.Segment("G/B", "more", trailingSpace = false), segs[1])
    }

    @Test
    fun parsePendingChordBeforeNextChord() {
        // Two chords in a row: the first is flushed as a chord-only segment.
        val segs = ChordLine.parse("[C][G]word")
        assertEquals(2, segs.size)
        assertEquals(ChordLine.Segment("C", "", trailingSpace = true), segs[0])
        assertEquals(ChordLine.Segment("G", "word", trailingSpace = false), segs[1])
    }

    @Test
    fun parseTrailingSpaceAndChordOnly() {
        val spaced = ChordLine.parse("[C]lyric ")
        assertEquals(listOf(ChordLine.Segment("C", "lyric", trailingSpace = true)), spaced)

        val only = ChordLine.parse("[Am]")
        assertEquals(listOf(ChordLine.Segment("Am", "", trailingSpace = false)), only)
    }

    @Test
    fun parseEmptyAndUnmatchedBracket() {
        assertTrue(ChordLine.parse("").isEmpty())
        assertEquals(emptyList<String>(), ChordLine.chords(""))
        assertEquals(emptyList<String>(), ChordLine.chords("[C"))

        val unmatched = ChordLine.parse("[C lyric")
        assertEquals(2, unmatched.size)
        assertEquals(null, unmatched[0].chord)
        assertEquals("[C", unmatched[0].text)
        assertTrue(unmatched[0].trailingSpace)
        assertEquals("lyric", unmatched[1].text)
    }
}
