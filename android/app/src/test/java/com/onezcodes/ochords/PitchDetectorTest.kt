package com.onezcodes.ochords

import com.onezcodes.ochords.services.PitchTracker
import com.onezcodes.ochords.services.YIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin

class PitchDetectorTest {
    private val rate = 48000.0

    /** A plucked-string-like tone: fundamental plus fading overtones. */
    private fun tone(hz: Double, seconds: Double, amplitude: Double = 0.1, harmonics: Int = 5): FloatArray {
        val n = (seconds * rate).toInt()
        return FloatArray(n) { i ->
            val t = i / rate
            var v = 0.0
            for (k in 1..harmonics) v += sin(2 * PI * hz * k * t) / (k * k)
            (amplitude * v).toFloat()
        }
    }

    private fun cents(a: Double, b: Double) = 1200 * log2(a / b)

    /** A deterministic "hiss" phase for the quiet room. */
    private fun i2(i: Int) = (i * 7919 % 1000) / 1000.0 * 2 * PI

    @Test
    fun yinFinds110HzWithinOneCent() {
        val r = YIN.pitch(tone(110.0, 0.1), rate)
        assertNotNull(r)
        assertTrue("aperiodicity ${r!!.aperiodicity}", r.aperiodicity < 0.15)
        assertTrue("read ${r.hz} Hz", abs(cents(r.hz, 110.0)) < 1)
    }

    @Test
    fun yinHasNoOctaveErrorWhenSecondHarmonicIsLouder() {
        // Low strings: the 2nd harmonic louder than the fundamental.
        val n = 4096
        val samples = FloatArray(n) { i ->
            val t = i / rate
            (0.05 * sin(2 * PI * 82.41 * t) + 0.1 * sin(2 * PI * 164.82 * t) + 0.03 * sin(2 * PI * 247.23 * t)).toFloat()
        }
        val r = YIN.pitch(samples, rate)!!
        assertTrue("read ${r.hz} Hz", abs(cents(r.hz, 82.41)) < 3)
    }

    @Test
    fun refineStaysWithinOneCent() {
        val hz = YIN.refine(tone(196.0, 0.17), near = 196.5, sampleRate = rate)
        assertNotNull(hz)
        assertTrue("refined to $hz", abs(cents(hz!!, 196.0)) < 1)
    }

    @Test
    fun trackerLocksOnANoteAndGoesQuietInSilence() {
        val tracker = PitchTracker()
        // Half a second of a quiet room (so the noise floor is known), then the note.
        val signal = FloatArray((0.5 * rate).toInt()) { (0.0002 * sin(i2(it))).toFloat() } + tone(110.0, 1.0)
        val hop = (0.02 * rate).toInt()
        val readings = ArrayList<Double>()
        var end = PitchTracker.shortWindow
        while (end <= signal.size) {
            val start = maxOf(0, end - PitchTracker.longWindow)
            tracker.read(signal.copyOfRange(start, end), hop, rate)?.let { readings.add(it.hz) }
            end += hop
        }
        assertTrue("got ${readings.size} readings", readings.size > 30)
        // iOS's synthetic-guitar figure: 98–99 % of readings within 5 ¢ (the one stray is the silence-to-note edge).
        val within = readings.count { abs(cents(it, 110.0)) < 5 }
        assertTrue("$within of ${readings.size} within 5 cents: ${readings.map { "%.2f".format(it) }}", within >= readings.size * 0.95)

        // Silence: nothing is reported.
        val quiet = FloatArray(PitchTracker.longWindow)
        var silent = 0
        repeat(30) { if (tracker.read(quiet, hop, rate) == null) silent++ }
        assertEquals(30, silent)
    }

    @Test
    fun manualTargetIgnoresADistantString() {
        val tracker = PitchTracker()
        tracker.target = 146.83
        // Only the low E (82.4 Hz) is ringing: well outside the D string's band.
        val signal = tone(82.41, 0.5)
        val hop = (0.02 * rate).toInt()
        var accepted = 0
        var end = PitchTracker.shortWindow
        while (end <= signal.size) {
            val start = maxOf(0, end - PitchTracker.longWindow)
            if (tracker.read(signal.copyOfRange(start, end), hop, rate) != null) accepted++
            end += hop
        }
        assertEquals(0, accepted)
    }
}
