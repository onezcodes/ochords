package com.onezcodes.ochords

import com.onezcodes.ochords.services.ChordTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTheoryTest {

    @Test
    fun transposeRootKeepsQuality() {
        assertEquals("Bm7", ChordTheory.transposeRoot("Am7", 2))
        assertEquals("C", ChordTheory.transposeRoot("Bb", 2))
        assertEquals("Am7", ChordTheory.transposeRoot("Am7", 0))
    }

    @Test
    fun transposeSlashBass() {
        assertEquals("A/C#", ChordTheory.transpose("G/B", 2))
        assertEquals("G/B", ChordTheory.transpose("G/B", 0))
        assertEquals("G/B", ChordTheory.transpose("G/B", 12))
    }

    @Test
    fun sharpSideBassSpelling() {
        // Eb/G +1 → E/Ab, respelled E/G# for sharp-side roots.
        assertEquals("E/G#", ChordTheory.transpose("Eb/G", 1))
        assertEquals("A/C#", ChordTheory.transpose("G/B", 2))
    }

    @Test
    fun shownEasySwap() {
        val easy = ChordTheory.shown("F", transpose = 0, capo = 0, easy = true)
        assertEquals("Fmaj7", easy.name)
        assertEquals("F", easy.from)
        assertTrue(easy.isEasy)

        val hard = ChordTheory.shown("F", transpose = 0, capo = 0, easy = false)
        assertEquals("F", hard.name)
        assertEquals(null, hard.from)
        assertFalse(hard.isEasy)
    }

    @Test
    fun isBarreDetectsOpenShapes() {
        assertFalse(ChordTheory.isBarre("Am"))
        assertFalse(ChordTheory.isBarre("C"))
        assertFalse(ChordTheory.isBarre("Fmaj7"))
        assertFalse(ChordTheory.isBarre("G/B")) // head is G, open
        assertTrue(ChordTheory.isBarre("F"))
        assertTrue(ChordTheory.isBarre("Bm"))
        assertTrue(ChordTheory.isBarre("Bb"))
    }

    @Test
    fun capoOptionsIncludesCapoZeroAndTracksBarres() {
        val chords = listOf("C", "G", "Am", "F")
        val options = ChordTheory.capoOptions(chords, songKey = "C", transpose = 0, current = 0)
        assertTrue(options.any { it.capo == 0 })
        assertEquals(0, options.first().capo)
        // Capo 0 shapes are the song chords; F is the only barre among them.
        val capo0 = options.first { it.capo == 0 }
        assertEquals(1, capo0.barres)
        assertEquals("C", capo0.shapeKey)
        // Options are filtered to open shape keys (plus capo 0 / current); barre counts stay accurate.
        assertTrue(options.all { it.barres == it.shapes.count(ChordTheory::isBarre) })
        assertTrue(options.size <= 4)
    }

    @Test
    fun transposeLineMovesBracketedChords() {
        assertEquals("[D]hello [A/C#]world", ChordTheory.transposeLine("[C]hello [G/B]world", 2))
        assertEquals("no chords here", ChordTheory.transposeLine("no chords here", 2))
        assertEquals("[C]hello", ChordTheory.transposeLine("[C]hello", 0))
    }
}
