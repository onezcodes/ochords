package com.onezcodes.ochords

import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.services.Box
import com.onezcodes.ochords.services.ChordSheetParser
import com.onezcodes.ochords.services.ScanTextLine
import com.onezcodes.ochords.services.ScanWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sheet parser on hand-positioned text lines (normalised page coordinates, origin top-left). */
class ChordSheetParserTest {

    /** A text line at `y`: each word at its x, as wide as its letters. */
    private fun line(y: Double, h: Double = 0.03, vararg words: Pair<String, Double>, confidence: Float = 0.95f) =
        ScanTextLine(words.map { (text, x) -> ScanWord(text, Box(x, y, 0.02 * text.length, h)) }, confidence)

    private val chordRow = line(0.22, 0.03, "C" to 0.10, "G" to 0.40)
    private val lyricRow = line(0.27, 0.03, "Morning" to 0.10, "light" to 0.26, "shines" to 0.40)

    @Test
    fun chordRowPairsWithLyricBelowAndPreambleGivesTitleArtistAndStrum() {
        val sheet = ChordSheetParser.parse(listOf(
            line(0.05, 0.05, "Morning" to 0.30, "Light" to 0.46),
            line(0.12, 0.03, "The" to 0.35, "Wanderers" to 0.43),
            line(0.16, 0.03, "↓" to 0.30, "↓" to 0.34, "↑" to 0.38, "↓" to 0.42),
            chordRow,
            lyricRow,
        ), readsMyanmar = false)
        val song = sheet.song
        assertEquals("Morning Light", song.title)
        assertEquals("The Wanderers", song.artist)
        assertEquals("C", song.key)
        assertEquals(1, song.sections.size)
        val verse = song.sections[0]
        assertEquals(SectionKind.Verse, verse.kind)
        assertEquals("VERSE 1", verse.label)
        assertEquals(listOf("[C]Morning light [G]shines"), verse.lines)
        assertEquals("↓ ↓ ↑ ↓", verse.strumPattern)
        assertTrue(sheet.needsLyrics.isEmpty())
        assertEquals(1, sheet.regions.size)
    }

    @Test
    fun titleAndSingerOnOneLineAreSplit() {
        val song = ChordSheetParser.parse(listOf(
            line(0.05, 0.05, "Morning" to 0.20, "Light" to 0.36, "–" to 0.48, "The" to 0.52, "Wanderers" to 0.60),
            chordRow,
            lyricRow,
        ), readsMyanmar = false).song
        assertEquals("Morning Light", song.title)
        assertEquals("The Wanderers", song.artist)
    }

    @Test
    fun myanmarLineWithLeadingLabelAndEndOfLineChord() {
        val sheet = ChordSheetParser.parse(listOf(
            line(0.05, 0.05, "ညီလေး" to 0.30),
            line(0.12, 0.03, "တေးဆို" to 0.30, "-" to 0.40, "မျိုးကြီး" to 0.43),
            line(0.20, 0.04, "V1" to 0.05, "မသေချာ" to 0.12, "Bm" to 0.50),
        ), readsMyanmar = true)
        val song = sheet.song
        assertEquals("ညီလေး", song.title)
        assertEquals("မျိုးကြီး", song.artist)
        assertEquals(1, song.sections.size)
        assertEquals("VERSE 1", song.sections[0].label)
        assertEquals(listOf("မသေချာ [Bm]"), song.sections[0].lines)
        assertEquals("Bm", song.key)
        assertTrue(song.isMyanmar)
    }

    @Test
    fun chordOverMyanmarWordSnapsToSyllableStart() {
        val song = ChordSheetParser.parse(listOf(
            line(0.20, 0.03, "G" to 0.20),
            line(0.25, 0.04, "စက်ရုပ်" to 0.10),
        ), readsMyanmar = true).song
        assertEquals(listOf("စက်[G]ရုပ်"), song.sections[0].lines)
        assertEquals(listOf(0, 2), ChordSheetParser.myanmarSyllableStarts("စက်ရုပ်"))
    }

    @Test
    fun gluedChordsAreSplitAcrossTheWord() {
        assertEquals(listOf("Am", "Em"), ChordSheetParser.gluedChords("AmEm"))
        assertEquals(listOf("C", "G/B"), ChordSheetParser.gluedChords("CG/B"))
        assertNull(ChordSheetParser.gluedChords("CAB"))
        val song = ChordSheetParser.parse(listOf(
            line(0.20, 0.03, "AmEm" to 0.10),
            line(0.25, 0.03, "hi" to 0.10, "world" to 0.14),
        ), readsMyanmar = false).song
        assertEquals(listOf("[Am]hi [Em]world"), song.sections[0].lines)
    }

    @Test
    fun barLinesReadAsLettersMakeAnInstrumentalRow() {
        val sheet = ChordSheetParser.parse(listOf(
            line(0.30, 0.03, "(ICI" to 0.10, "|" to 0.20, "Am" to 0.25, "|" to 0.32, "G" to 0.36, "|" to 0.42, "IGI)" to 0.46),
        ), readsMyanmar = false)
        val intro = sheet.song.sections.single()
        assertEquals(SectionKind.Music, intro.kind)
        assertEquals("INTRO", intro.label)
        assertEquals(listOf("C", "Am", "G", "G"), intro.bars)
        assertEquals("4 bars", intro.note)
        assertTrue(sheet.chordNotes["C"]!!.startsWith("Bar line"))
        assertEquals(ChordSheetParser.Token.Bar, ChordSheetParser.token("x2"))
        assertEquals(ChordSheetParser.Token.Bar, ChordSheetParser.barToken("||"))
    }

    @Test
    fun chordDiagramLegendIsDropped() {
        val song = ChordSheetParser.parse(listOf(
            line(0.05, 0.05, "Song" to 0.30, "Title" to 0.40),
            line(0.12, 0.03, "C" to 0.10, "G" to 0.20, "Am" to 0.30, "F" to 0.40),
            line(0.16, 0.03, "x32010" to 0.10, "320003" to 0.20, "x02210" to 0.30, "133211" to 0.40),
            line(0.25, 0.03, "C" to 0.10),
            line(0.30, 0.03, "la" to 0.10, "la" to 0.16, "la" to 0.22),
        ), readsMyanmar = false).song
        assertEquals("Song Title", song.title)
        assertEquals(1, song.sections.size)
        assertEquals(SectionKind.Verse, song.sections[0].kind)
        assertEquals(listOf("[C]la la la"), song.sections[0].lines)
    }

    @Test
    fun repeatMarkOnHeadingBecomesTheSectionNote() {
        val song = ChordSheetParser.parse(listOf(
            line(0.10, 0.03, "Chorus" to 0.10, "x2" to 0.25),
            chordRow,
            lyricRow,
        ), readsMyanmar = false).song
        val chorus = song.sections.single()
        assertEquals(SectionKind.Chorus, chorus.kind)
        assertEquals("CHORUS", chorus.label)
        assertEquals("×2", chorus.note)
        assertEquals(listOf("[C]Morning light [G]shines"), chorus.lines)
    }

    @Test
    fun keyCapoAndTempoAreRead() {
        val song = ChordSheetParser.parse(listOf(
            line(0.05, 0.05, "Morning" to 0.30, "Light" to 0.46),
            line(0.12, 0.03, "Key:" to 0.10, "G" to 0.18, "Capo" to 0.30, "2" to 0.40, "120" to 0.50, "bpm" to 0.58),
            chordRow,
            lyricRow,
        ), readsMyanmar = false).song
        assertEquals("G", song.key)
        assertEquals(120, song.tempo)
        assertEquals("Capo 2", song.myNote)
        assertEquals("Morning Light", song.title)
    }

    @Test
    fun rewordKeepsChordsWithTheirWords() {
        // A fixed typo keeps its chord at the same spot in the word.
        assertEquals("[C]Morning li[G]ght shine", ChordSheetParser.reword("[C]Morning li[G]ght shines", "Morning light shine"))
        // A chord on a deleted word moves to the next word left.
        assertEquals("[C]Morning [G][Am]shines", ChordSheetParser.reword("[C]Morning [G]light [Am]shines", "Morning shines"))
        assertEquals("Morning light", ChordSheetParser.words("[C]Morning [G]light"))
        assertEquals("[C]sing [G]along", ChordSheetParser.placeChords("[C]hello [G]world", "sing along"))
    }

    @Test
    fun creditsAndTitlesAreCleaned() {
        val credit = ChordSheetParser.credit("တေးရေး-ကန် တေးဆို-မျိုးကြီး")!!
        assertEquals("မျိုးကြီး", credit.singer)
        assertEquals("ကန်", credit.writer)
        assertEquals("The Wanderers", ChordSheetParser.credit("by The Wanderers")!!.singer)
        assertNull(ChordSheetParser.credit("Stand by me"))
        assertEquals("Mary", ChordSheetParser.cleanTitle("6/8 beat Mary"))
        assertEquals("ညီလေး", ChordSheetParser.cleanTitle("ခေါင်းစဉ်: \"ညီလေး\""))
        assertEquals("Mary", ChordSheetParser.cleanTitle("Title: Mary."))
    }
}
