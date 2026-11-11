package com.onezcodes.ochords.services

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fundamental frequency of a sound, by the YIN algorithm (de Cheveigné & Kawahara, 2002) — robust to the strong
 * overtones of a plucked string, which fool plain autocorrelation into octave errors.
 */
object YIN {
    data class Result(
        val hz: Double,
        /** 0 = perfectly periodic … 1 = noise. Readings above ~0.2 aren't a clear note. */
        val aperiodicity: Double,
    )

    /**
     * @param samples mono audio, a few periods of the lowest note long (≥ 2048 samples at 44.1/48 kHz for low D).
     * @param range frequencies to look for. Guitar: low D (73 Hz) on a dropped string up to high frets.
     */
    fun pitch(samples: FloatArray, sampleRate: Double, range: ClosedFloatingPointRange<Double> = 60.0..1100.0, threshold: Double = 0.15): Result? {
        val maxLag = min(samples.size / 2, (sampleRate / range.start).toInt())
        val minLag = max(2, (sampleRate / range.endInclusive).toInt())
        if (maxLag <= minLag + 2) return null
        val window = samples.size - maxLag

        // Difference function d(τ) = Σx² + Σx²(shifted) − 2·Σx·x(shifted): the two energies come from a running sum
        // of squares and the cross term is one dot product per lag.
        val d = DoubleArray(maxLag + 1)
        val squares = DoubleArray(samples.size + 1)
        for (i in samples.indices) squares[i + 1] = squares[i] + (samples[i] * samples[i]).toDouble()
        val head = squares[window]
        for (lag in 1..maxLag) {
            var cross = 0f
            for (i in 0 until window) cross += samples[i] * samples[i + lag]
            d[lag] = max(0.0, head + (squares[lag + window] - squares[lag]) - 2 * cross.toDouble())
        }
        val normalised = DoubleArray(maxLag + 1) { 1.0 }
        var running = 0.0
        for (lag in 1..maxLag) {
            running += d[lag]
            normalised[lag] = if (running > 0) d[lag] * lag / running else 1.0
        }

        // The first dip under the threshold is the period (later dips are its multiples — lower octaves).
        var lag = minLag
        while (lag < maxLag) {
            if (normalised[lag] < threshold) {
                while (lag + 1 < maxLag && normalised[lag + 1] < normalised[lag]) lag += 1
                break
            }
            lag += 1
        }
        if (lag >= maxLag) {
            // No clear dip: take the deepest one, and let the caller reject it by its aperiodicity.
            lag = (minLag until maxLag).minByOrNull { normalised[it] } ?: return null
        }

        // Octave check: on real, noisy sound a multiple of the period can dip under the threshold first. If a nearly
        // as deep dip sits at ½, ⅓ or ¼ of it, that shorter period is the note (a string's own odd overtones keep
        // the half-period dip shallow, so this doesn't push notes an octave up).
        val picked = normalised[lag]
        for (divisor in intArrayOf(4, 3, 2)) {
            val centre = lag / divisor
            val lo = max(minLag, (centre * 0.97).toInt())
            val hi = min(maxLag - 1, ceil(centre * 1.03).toInt())
            if (lo >= hi) continue
            val dip = (lo..hi).minByOrNull { normalised[it] } ?: continue
            if (dip > lo && dip < hi && normalised[dip] < 0.3 && normalised[dip] < picked + 0.2) {
                lag = dip
                break
            }
        }

        // Parabolic interpolation between neighbouring lags for sub-sample precision (≈ 0.1 cent).
        var period = lag.toDouble()
        if (lag > 1 && lag < maxLag) {
            val a = normalised[lag - 1]
            val b = normalised[lag]
            val c = normalised[lag + 1]
            val denominator = a - 2 * b + c
            if (abs(denominator) > 1e-12) period += 0.5 * (a - c) / denominator
        }
        return Result(hz = sampleRate / period, aperiodicity = normalised[lag])
    }

    /**
     * Re-measures a note from its fundamental and 2nd harmonic only. A plucked string's higher overtones run sharp
     * (stiff, wound strings most) and are loudest just after the pluck, which pulls a plain reading several cents
     * high — the "too high, then too low" a player chases. The 2nd harmonic is within half a cent of true and much
     * stronger than the fundamental on low strings, so keeping it steadies the reading.
     */
    fun refine(samples: FloatArray, near: Double, sampleRate: Double): Double? {
        val range = (near * 0.92)..(near * 1.08)
        val band = isolate(samples, from = near * 0.75, to = near * 2.2, sampleRate = sampleRate)
        val r = pitch(band, sampleRate, range, threshold = 0.3) ?: return null
        return if (r.hz in range) r.hz else null
    }

    /**
     * Keeps only the band `from`–`to` Hz (fourth-order Butterworth high- and low-pass); the filters' start-up is
     * dropped.
     */
    fun isolate(samples: FloatArray, from: Double, to: Double, sampleRate: Double): FloatArray {
        val x = samples.copyOf()
        for (q in doubleArrayOf(0.5412, 1.3066)) {
            biquad(x, cutoff = from, q = q, highPass = true, sampleRate = sampleRate)
            biquad(x, cutoff = to, q = q, highPass = false, sampleRate = sampleRate)
        }
        return x.copyOfRange(min(512, x.size / 8), x.size)
    }

    /** RBJ-cookbook biquad low- or high-pass, in place. */
    private fun biquad(x: FloatArray, cutoff: Double, q: Double, highPass: Boolean, sampleRate: Double) {
        val w = 2 * PI * cutoff / sampleRate
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        val b0 = (if (highPass) (1 + cos(w)) else (1 - cos(w))) / 2 / a0
        val b1 = (if (highPass) -(1 + cos(w)) else (1 - cos(w))) / a0
        val b2 = b0
        val a1 = -2 * cos(w) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val x0 = x[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
            x[i] = y0.toFloat()
        }
    }
}

/**
 * Turns windows of microphone audio into tuner readings: the loudness gate, the lock on the note being tuned and
 * octave handling. Pure logic (no audio APIs) so it can be tested with recorded or synthesised sound.
 * Not thread-safe: use from one thread (the detector's analysis coroutine).
 */
class PitchTracker {
    data class Reading(
        val hz: Double,
        /** Loudness (RMS) of the window. */
        val level: Float,
    )

    companion object {
        /**
         * Samples analysed at once: ≈ 85 ms at 48 kHz — several periods of a dropped low D — so the needle follows a
         * turning peg quickly…
         */
        const val shortWindow = 4096
        /** …and ≈ 170 ms once a locked note fades close to the room's noise: twice the cycles average the noise out. */
        const val longWindow = 8192
        /** A locked note counts as fading below this many times the noise floor. */
        private const val fadingBelow = 5f
        /** A new note must be this many times louder than the room… */
        private const val aboveNoise = 2.5f
        /** …never quieter than this (a dead-silent room would otherwise make hiss look like a note)… */
        private const val quietest = 0.0006f
        /** …and never needs to be louder than this, however noisy the room (a soft pluck near the phone). */
        private const val loudestGate = 0.004f
        /** A locked note is followed until it fades to this many times the room's noise (below it, readings jitter). */
        private const val followAbove = 1.6f
        /** The room's noise is the quietest moment of the last few seconds (≈ 150 readings). */
        private const val noiseMemory = 150
        /**
         * A new note must be clearly periodic — under YIN's threshold, so it's the first clear dip and not the deepest
         * one, which is often a lower octave; the note already locked on may get noisier as it fades.
         */
        private const val newNoteAperiodicity = 0.15
        private const val lockedAperiodicity = 0.35
        /** Readings within this many cents of the locked note keep the lock. */
        private const val lockCents = 60.0
        /** The lock is let go after this long without the note… */
        private const val lockSeconds = 0.4
        /**
         * …or at once when a new pluck makes the sound jump this much within 100 ms (5 readings), so a string just
         * plucked takes over from one still ringing.
         */
        private const val pluckJump = 1.8f
        private const val pluckLookback = 5
        /** Octave and twelfth errors (in cents) a fading note can slip into. */
        private val octaveSlips = doubleArrayOf(-1902.0, -1200.0, 1200.0, 1902.0)
    }

    /** Recent loudness, newest last, for the noise floor. */
    private val levels = ArrayDeque<Float>()
    private val noiseFloor: Float get() = levels.minOrNull() ?: 0.001f
    private var locked: Double? = null

    /**
     * The note being tuned when the player picked a string (manual mode): only pitches within a fifth of it are
     * looked for, so other strings still ringing — or an octave of the note — can't be taken for it.
     */
    var target: Double? = null
    /** Samples since the locked note was last heard. */
    private var sinceLocked = 0

    /**
     * @param window the newest audio, oldest first: at least [shortWindow] samples, up to [longWindow].
     * @param elapsed samples since the last reading.
     */
    fun read(window: FloatArray, elapsed: Int, sampleRate: Double): Reading? {
        val recent = if (window.size > shortWindow) window.copyOfRange(window.size - shortWindow, window.size) else window
        val level = MicInput.rms(recent)
        levels.addLast(level)
        while (levels.size > noiseMemory) levels.removeFirst()
        sinceLocked += elapsed
        if (sinceLocked > lockSeconds * sampleRate) locked = null

        if (locked != null && levels.size > pluckLookback && level > levels[levels.size - 1 - pluckLookback] * pluckJump) {
            locked = null
        }

        val fading = locked != null && level < noiseFloor * fadingBelow
        val samples = if (fading) window else recent
        // A locked note is followed down to the quietest level; a new one must stand out from the room.
        val gate = max(quietest, if (locked != null) noiseFloor * followAbove else min(loudestGate, noiseFloor * aboveNoise))
        val t = target
        val range = if (t != null) (t * 2.0.pow(-7.0 / 12))..(t * 2.0.pow(7.0 / 12)) else 60.0..1100.0
        // With a string chosen, listen only to the band around it (about ±5 semitones, so its neighbours drop out).
        val heard = if (t != null) YIN.isolate(samples, from = t * 0.75, to = t * 1.33, sampleRate = sampleRate) else samples
        val found = if (level >= gate) YIN.pitch(heard, sampleRate, range) else null
        val accepted: Double? = found?.let { f ->
            val l = locked
            if (l != null) {
                val cents = 1200 * log2(f.hz / l)
                if (abs(cents) < lockCents) return@let if (f.aperiodicity <= lockedAperiodicity) f.hz else null
                // A fading string can read an octave (or a twelfth) off; that's the same note, not a new one.
                if (octaveSlips.any { abs(cents - it) < lockCents }) return@let null
            }
            // A chosen string can't be confused with an octave (the search stays near it), so a reading muddied by
            // other strings still ringing is good enough to start on.
            val limit = if (target == null) newNoteAperiodicity else lockedAperiodicity
            if (f.aperiodicity <= limit) f.hz else null
        }
        val rough = accepted ?: return null
        val hz = YIN.refine(samples, near = rough, sampleRate = sampleRate) ?: rough
        locked = hz
        sinceLocked = 0
        return Reading(hz = hz, level = level)
    }
}

/**
 * Listens to the microphone and reports the pitch being played, 50 times a second.
 * Start and stop on the main thread; readings arrive on the main thread ([onReading] and the observable [reading]).
 *
 * Like dedicated tuner apps it follows a note for as long as the string rings: the loudness gate adapts to the
 * room instead of a fixed level, and once a note is found it stays locked while the fading string gets noisier.
 */
class PitchDetector {
    /** Each clear note, or null while it's quiet or noisy. Main thread. */
    var onReading: ((PitchTracker.Reading?) -> Unit)? = null
    /** The latest reading (null while quiet or noisy), for Compose. */
    var reading: PitchTracker.Reading? by mutableStateOf(null)
        private set

    private val mic = MicInput(windowLength = PitchTracker.longWindow)
    private val main = Handler(Looper.getMainLooper())
    /** Only touched on the mic's analysis coroutine. */
    private var tracker = PitchTracker()
    private var target: Double? = null

    val isRunning: Boolean get() = mic.isRunning

    init {
        mic.onWindow = { window, elapsed, sampleRate ->
            if (window.size >= PitchTracker.shortWindow) {
                val r = tracker.read(window, elapsed, sampleRate)
                main.post { deliver(r) }
            }
        }
        mic.onIgnored = { main.post { deliver(null) } }
    }

    private fun deliver(r: PitchTracker.Reading?) {
        reading = r
        onReading?.invoke(r)
    }

    /** Throws [MicInput.Failure] when the microphone can't be opened (permission off, hardware busy). */
    fun start() = mic.start()

    fun stop() {
        if (!mic.isRunning) return
        mic.stop()
        mic.perform {
            tracker = PitchTracker()
            tracker.target = target
        }
    }

    /** Listen only around this note (the string the player picked), or anywhere (null, auto mode). */
    fun setTarget(hz: Double?) {
        mic.perform {
            target = hz
            tracker.target = hz
        }
    }

    /** Stops reporting for `seconds` — while the app plays its own sound — so it's never read as a string. */
    fun ignore(seconds: Double) = mic.ignore(seconds)

    /** Frees the detector's background work; call when the screen that owns it is gone for good. */
    fun close() = mic.close()
}
