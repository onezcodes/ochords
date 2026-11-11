package com.onezcodes.ochords

import com.onezcodes.ochords.services.ChordCheck
import com.onezcodes.ochords.services.ChordCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ChordCheckTest {
    private val rate = 48000.0

    /** Half a second of the strings that ring: each a fundamental plus fading overtones. */
    private fun strum(hz: List<Double>): FloatArray {
        val n = (0.5 * rate).toInt()
        val out = FloatArray(n)
        for ((s, f) in hz.withIndex()) {
            val phase = s * 0.7
            for (i in 0 until n) {
                val t = i / rate
                var v = 0.0
                for (k in 1..8) v += sin(2 * PI * f * k * t + phase * k) / (k * 1.3) * exp(-t * (1 + k * 0.5))
                out[i] += (0.08 * v).toFloat()
            }
        }
        return out
    }

    @Test
    fun cleanEMajorIsClearWhereItCanBeJudged() {
        val notes = ChordCheck.notes("022100").map { it!! }
        val verdicts = ChordCheck.verdicts(strum(notes), rate, "022100")
        // E3, B3 and E4 sit on the octaves of lower strings, so they can't be told apart.
        assertEquals(listOf(Verdict.Clear, Verdict.Clear, Verdict.Unclear, Verdict.Clear, Verdict.Unclear, Verdict.Unclear), verdicts)
        assertTrue(verdicts.all { it.isGood })
    }

    @Test
    fun aDeadStringIsFound() {
        val notes = ChordCheck.notes("022100").map { it!! }
        val ringing = notes.filterIndexed { i, _ -> i != 3 } // the G♯ (string 4) is muffled
        val verdicts = ChordCheck.verdicts(strum(ringing), rate, "022100")
        assertEquals(Verdict.Dead, verdicts[3])
        assertEquals(Verdict.Clear, verdicts[0])
        assertEquals(Verdict.Clear, verdicts[1])
    }

    @Test
    fun aMutedStringThatRingsIsCaught() {
        // A minor: low E should be muted.
        val expected = ChordCheck.notes("x02210")
        val clean = strum(expected.filterNotNull())
        assertEquals(Verdict.Muted, ChordCheck.verdicts(clean, rate, "x02210")[0])
        val leaking = strum(listOf(ChordCheck.openStrings[0]) + expected.filterNotNull())
        assertEquals(Verdict.Ringing, ChordCheck.verdicts(leaking, rate, "x02210")[0])
    }

    @Test
    fun notesOfAPattern() {
        val n = ChordCheck.notes("x32010")
        assertEquals(null, n[0])
        assertEquals(130.81, n[1]!!, 0.05) // C3 on the A string
        assertEquals(329.63, n[5]!!, 0.01)
    }

    @Test
    fun nonNegativeLeastSquaresRecoversGains() {
        val t1 = doubleArrayOf(1.0, 0.5, 0.0)
        val t2 = doubleArrayOf(0.0, 1.0, 0.5)
        val y = DoubleArray(3) { 2 * t1[it] + 3 * t2[it] }
        val g = ChordCheck.nonNegativeLeastSquares(listOf(t1, t2), y)
        assertEquals(2.0, g[0], 1e-6)
        assertEquals(3.0, g[1], 1e-6)
        // A template pulling the wrong way is clamped at zero.
        val g2 = ChordCheck.nonNegativeLeastSquares(listOf(t1, doubleArrayOf(-1.0, 0.0, 0.0)), doubleArrayOf(1.0, 0.5, 0.0))
        assertEquals(0.0, g2[1], 1e-9)
    }
}
