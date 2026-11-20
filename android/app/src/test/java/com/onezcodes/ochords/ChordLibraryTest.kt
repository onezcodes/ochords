package com.onezcodes.ochords

import com.onezcodes.ochords.services.ChordLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChordLibraryTest {

    @Test
    fun infoReturnsCPattern() {
        val c = ChordLibrary.info("C")
        assertNotNull(c)
        assertEquals("C major", c!!.full)
        assertEquals("x32010", c.voicings.first().pattern)
    }

    @Test
    fun slashChordFallsBackToRoot() {
        // Am/C is not listed; falls back to Am.
        val amc = ChordLibrary.info("Am/C")
        assertNotNull(amc)
        assertEquals("Am", amc!!.name)
        assertEquals("x02210", amc.voicings.first().pattern)

        // G/B has its own entry.
        assertEquals("x20003", ChordLibrary.info("G/B")!!.voicings.first().pattern)

        assertNull(ChordLibrary.info("Xyz"))
    }

    @Test
    fun chordsInKeyCAndG() {
        val c = ChordLibrary.chordsInKey("C")
        assertEquals(
            listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"),
            c.map { it.chord },
        )
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), c.map { it.degree })

        val g = ChordLibrary.chordsInKey("G")
        assertEquals(
            listOf("G", "Am", "Bm", "C", "D", "Em", "F#dim"),
            g.map { it.chord },
        )
    }
}
