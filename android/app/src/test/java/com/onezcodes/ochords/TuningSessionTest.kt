package com.onezcodes.ochords

import com.onezcodes.ochords.services.TuningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningSessionTest {
    private val standard = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    @Test
    fun stringCountsAsTunedAfterHoldingWithinTolerance() {
        val session = TuningSession(standard, selected = 1)
        var tunedAt: Double? = null
        var t = 0.0
        while (t < 1.0) {
            val events = session.hear(110.1, t)
            if (events.any { it is TuningSession.Event.Tuned }) { tunedAt = t; break }
            t += 0.02
        }
        assertTrue("tuned at $tunedAt", tunedAt != null && tunedAt >= TuningSession.holdToTune - 1e-9)
        assertTrue(1 in session.tuned)
        assertFalse(session.allTuned)
    }

    @Test
    fun autoModeSwitchesAfterThreeConsistentReadings() {
        val session = TuningSession(standard, selected = 1)
        val events = (0 until 3).flatMap { session.hear(196.0, it * 0.02) }
        assertEquals(listOf<TuningSession.Event>(TuningSession.Event.Switched(3)), events)
        assertEquals(3, session.selected)
    }

    @Test
    fun oneStrayReadingDoesNotSwitch() {
        val session = TuningSession(standard, selected = 1)
        session.hear(110.0, 0.0)
        session.hear(196.0, 0.02)
        session.hear(110.0, 0.04)
        assertEquals(1, session.selected)
    }

    @Test
    fun readingGoesStaleAfterHoldAfterNote() {
        val session = TuningSession(standard, selected = 1)
        session.hear(110.0, 0.0)
        session.hear(null, 0.1)
        assertEquals(110.0, session.heard!!, 1e-9)
        session.hear(null, 0.5)
        assertNull(session.heard)
    }

    @Test
    fun tunedStringLosesTickOnlyAfterClearlyOutForLong() {
        val session = TuningSession(standard, selected = 1)
        var t = 0.0
        while (t <= 0.5) { session.hear(110.0, t); t += 0.02 }
        assertTrue(1 in session.tuned)
        // A pause, then a new pluck 30 cents flat (≈ 108.1 Hz): the attack is ignored, then 1.5 s out loses the tick.
        session.hear(null, 0.9)
        var untuned = false
        t = 1.0
        while (t < 4.0) {
            if (session.hear(108.1, t).any { it is TuningSession.Event.Untuned }) { untuned = true; break }
            t += 0.02
        }
        assertTrue(untuned)
        assertTrue("untuned at $t", t >= 1.0 + TuningSession.attack + TuningSession.untuneAfter - 0.05)
    }
}
