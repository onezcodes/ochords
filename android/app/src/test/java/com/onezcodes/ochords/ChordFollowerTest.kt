package com.onezcodes.ochords

import com.onezcodes.ochords.services.ChordFollower
import com.onezcodes.ochords.services.ChordRecognizer
import com.onezcodes.ochords.services.RealFFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ChordFollowerTest {
    @Test
    fun repeatCountReadsEveryWrittenForm() {
        assertEquals(2, ChordFollower.repeatCount("Sing it loud x2"))
        assertEquals(2, ChordFollower.repeatCount("Sing it loud (x2)"))
        assertEquals(3, ChordFollower.repeatCount("Sing it loud x 3"))
        assertEquals(2, ChordFollower.repeatCount("Sing it loud ×2"))
        assertEquals(2, ChordFollower.repeatCount("Sing it loud 2x"))
        assertEquals(2, ChordFollower.repeatCount("Sing it loud 2 times"))
        assertEquals(4, ChordFollower.repeatCount(") x4"))
        assertNull(ChordFollower.repeatCount("Sing it loud"))
        assertNull(ChordFollower.repeatCount("Track 2"))
        assertNull(ChordFollower.repeatCount("x1"))
    }

    @Test
    fun playOrderRepeatsALineAndABracketedGroup() {
        val lines = listOf("[C]One line", "[G]Again x2", "( [Am]Open", "[F]Close ) x2", "[C]Last")
        val order = ChordFollower.playOrder(lines)
        assertEquals(
            listOf(
                ChordFollower.Play(0, 1, 1),
                ChordFollower.Play(1, 1, 2), ChordFollower.Play(1, 2, 2),
                ChordFollower.Play(2, 1, 2), ChordFollower.Play(3, 1, 2),
                ChordFollower.Play(2, 2, 2), ChordFollower.Play(3, 2, 2),
                ChordFollower.Play(4, 1, 1),
            ),
            order,
        )
    }

    @Test
    fun bracketWithoutACountIsJustAnAside() {
        val order = ChordFollower.playOrder(listOf("(spoken)", "[C]Verse"))
        assertEquals(listOf(ChordFollower.Play(0, 1, 1), ChordFollower.Play(1, 1, 1)), order)
    }

    @Test
    fun followerAdvancesAfterTwoReadingsOfTheNextChord() {
        val steps = listOf(
            ChordFollower.Step("C", item = 0, line = 0),
            ChordFollower.Step("G", item = 0, line = 0),
            ChordFollower.Step("Am", item = 0, line = 1),
            ChordFollower.Step("F", item = 0, line = 1),
        )
        val f = ChordFollower(steps)
        val c = ChordRecognizer.template("C")!!
        val g = ChordRecognizer.template("G")!!
        assertFalse(f.hear(c, 0.05f))
        assertTrue(f.hear(c, 0.05f))
        assertEquals("C", f.current?.chord)
        assertFalse(f.hear(c, 0.05f))
        assertFalse(f.hear(g, 0.05f))
        assertTrue(f.hear(g, 0.05f))
        assertEquals("G", f.current?.chord)
        // Quiet readings never move it.
        repeat(5) { assertFalse(f.hear(ChordRecognizer.template("Am"), 0.0001f)) }
        assertEquals(1, f.position)
    }

    @Test
    fun indistinctNeighboursAreFoldedIntoOneStep() {
        val f = ChordFollower(listOf(
            ChordFollower.Step("C", 0, 0), ChordFollower.Step("C", 0, 0),
            ChordFollower.Step("Cmaj7", 0, 1), ChordFollower.Step("G", 0, 1),
        ))
        assertEquals(listOf("C", "G"), f.steps.map { it.chord })
    }

    @Test
    fun resyncWaitsForTheTappedLine() {
        val f = ChordFollower(listOf(
            ChordFollower.Step("C", 0, 0), ChordFollower.Step("G", 0, 1), ChordFollower.Step("Am", 1, 0),
        ))
        f.resync(item = 1, line = 0)
        assertEquals(1, f.position)
        assertTrue(f.hear(ChordRecognizer.template("Am"), 0.05f).let { it || f.hear(ChordRecognizer.template("Am"), 0.05f) })
        assertEquals("Am", f.current?.chord)
    }

    @Test
    fun chromaOfASynthesisedChordMatchesItsTemplateBest() {
        val rate = 48000.0
        val notes = listOf(130.81, 164.81, 196.0, 261.63, 329.63) // C3 E3 G3 C4 E4
        val samples = FloatArray(ChordRecognizer.window) { i ->
            val t = i / rate
            var v = 0.0
            for (hz in notes) for (k in 1..4) v += sin(2 * PI * hz * k * t) / k
            (0.05 * v).toFloat()
        }
        val chroma = ChordRecognizer.chroma(samples, rate)!!
        val best = listOf("C", "Am", "F", "G", "Em", "Dm", "C7").maxByOrNull { ChordRecognizer.similarity(chroma, ChordRecognizer.template(it)!!) }
        assertEquals("C", best)
    }

    @Test
    fun fftFindsAPureToneInTheRightBin() {
        val n = 4096
        val input = FloatArray(n) { sin(2 * PI * 100 * it / n).toFloat() }
        val mags = RealFFT.magnitudes(input)
        val peak = mags.indices.maxByOrNull { mags[it] }
        assertEquals(100, peak)
        assertTrue(abs(mags[100] - n / 2) < 1e-2 * n)
    }
}
