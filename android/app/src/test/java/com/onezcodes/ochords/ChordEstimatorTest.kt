package com.onezcodes.ochords

import com.onezcodes.ochords.services.ChordEstimator
import com.onezcodes.ochords.services.ChordRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChordEstimatorTest {
    @Test
    fun namesASynthesisedCMajorChroma() {
        val estimator = ChordEstimator()
        val chroma = ChordRecognizer.template("C")!!
        for (i in 0 until 10) estimator.hear(chroma, i * ChordEstimator.frameInterval)
        val last = estimator.segments.last()
        assertEquals("C", last.chord)
        assertTrue("confidence ${last.confidence}", last.confidence >= 90)
    }

    @Test
    fun followsAChangeAndPredictsTheNextChord() {
        val estimator = ChordEstimator()
        val c = ChordRecognizer.template("C")!!
        val g = ChordRecognizer.template("G")!!
        var t = 0.0
        repeat(2) {
            repeat(8) { estimator.hear(c, t); t += ChordEstimator.frameInterval }
            repeat(8) { estimator.hear(g, t); t += ChordEstimator.frameInterval }
        }
        assertEquals(listOf("C", "G", "C", "G"), estimator.segments.map { it.chord })
        assertEquals("G", estimator.likelyNext("C"))
    }

    @Test
    fun noiseThatFitsNoChordIsSilence() {
        val estimator = ChordEstimator()
        val flat = DoubleArray(12) { 1 / Math.sqrt(12.0) }
        for (i in 0 until 10) estimator.hear(flat, i * ChordEstimator.frameInterval)
        assertTrue(estimator.segments.isEmpty())
    }

    @Test
    fun keyOfCommonProgressionIsC() {
        val (key, confidence) = ChordEstimator.key(listOf("C", "G", "Am", "F", "C", "G", "F", "C"))
        assertEquals("C", key)
        assertTrue(confidence in 30..99)
    }

    @Test
    fun capoSuggestedOnlyWhenItOpensTwoMoreShapes() {
        assertEquals(0, ChordEstimator.capo(listOf("C", "G", "Am", "F")))
        // F# B C#m: capo 4 makes D G Am.
        assertEquals(4, ChordEstimator.capo(listOf("F#", "B", "C#m")))
    }

    @Test
    fun tempoFromAPulsedEnvelope() {
        // A strum every 0.5 s (120 BPM) for 20 s, at 50 readings a second.
        val envelope = ArrayList<Double>()
        for (i in 0 until 1000) {
            val inBeat = i % 25
            envelope.add(if (inBeat == 0) 0.2 else 0.05 * Math.exp(-inBeat / 10.0) + 0.01)
        }
        val bpm = ChordEstimator.tempo(envelope)
        assertNotNull(bpm)
        assertTrue("bpm $bpm", abs(bpm!! - 120) < 1)
    }

    @Test
    fun partsLetterRepeatedPhrases() {
        val bars = listOf("C", "G", "Am", "F", "C", "G", "Am", "F", "D", "A", "Bm", "G", "C", "G", "Am", "F")
        val parts = ChordEstimator.parts(bars)
        assertEquals(listOf('A', 'B', 'A'), parts.map { it.letter })
        assertEquals(0..7, parts[0].range)
        assertEquals(8..11, parts[1].range)
        assertEquals(12..15, parts[2].range)
    }

    @Test
    fun summaryGivesOneChordPerBar() {
        // Half a second of silence, then at 120 BPM (2 s bars): C for 2 bars, then G for 2 bars, strummed on every beat.
        val estimator = ChordEstimator()
        val c = ChordRecognizer.template("C")!!
        val g = ChordRecognizer.template("G")!!
        val seconds = 8.5
        var t = 0.0
        while (t < seconds - 1e-9) {
            estimator.hear(if (t < 0.5) null else if (t < 4.5) c else g, t)
            t += ChordEstimator.frameInterval
        }
        for (i in 0 until (seconds * ChordEstimator.envelopeRate).toInt()) {
            val inBeat = i % 25
            estimator.hearLoudness(if (i < 25) 0.001 else if (inBeat == 0) 0.2 else 0.05 * Math.exp(-inBeat / 10.0) + 0.01)
        }
        val s = estimator.summary()
        assertEquals(listOf("C", "C", "G", "G"), s.bars.map { it.chord })
        // Two bars each of C and G is ambiguous between the two keys (G carries the most weight); either is right.
        assertTrue("key ${s.key}", s.key == "C" || s.key == "G")
        assertNotNull(s.bpm)
        assertTrue("bpm ${s.bpm}", abs(s.bpm!! - 120) <= 1)
    }
}
