package com.onezcodes.ochords.services

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Which strings of a strummed chord rang. A chord's notes overlap — in C, the B string's C4 is the octave of the
 * A string's C3 — so "is there energy at C4" can't tell a ringing B string from the A string's overtone.
 * Instead the spectrum is explained as a sum of six strings, each a fundamental plus its overtones, and a
 * non-negative least-squares fit finds how loud each string must have been.
 */
object ChordCheck {
    enum class Verdict {
        /** Fretted or open string that rang. */
        Clear,
        /** Should ring but barely did (muffled by a finger, not pressed down). */
        Dead,
        /** Should be muted (x) and is. */
        Muted,
        /** Should be muted (x) but rang. */
        Ringing,
        /** Its sound sits under other strings' overtones (an octave of a lower string), so it can't be judged. */
        Unclear;

        val isGood: Boolean get() = this != Dead && this != Ringing
    }

    /** Open strings, low E to high e, at A4 = 440 Hz. */
    val openStrings = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    /** A string counts as ringing at this share of the loudest string's strength… */
    const val ringsAt = 0.12
    /** …and a muted string as wrongly ringing above this (muted strings still pick up a little sympathetic hum). */
    const val leaksAt = 0.22

    /** A string's own note must be at least this share of the strength the fit gives it (phone mics lose some bass). */
    const val fundamentalAtLeast = 0.4
    /** Overtones considered per string. */
    private const val overtones = 10
    /**
     * Two overtone shapes per string, bright and mellow (fundamental = 1); the fit mixes them, which covers how
     * differently strings, guitars and pick positions sound.
     */
    private val shapes: List<DoubleArray> = listOf(0.5, 1.4).map { slope -> DoubleArray(overtones) { (it + 1).toDouble().pow(-slope) } }

    /** Frequency each string should sound in a shape like "x32010" (null = muted). */
    fun notes(pattern: String, a4: Double = 440.0): List<Double?> =
        pattern.take(6).mapIndexed { i, c ->
            val fret = c.digitToIntOrNull() ?: return@mapIndexed null
            openStrings[i] * 2.0.pow(fret / 12.0) * a4 / 440
        }

    /**
     * @param samples mono audio of the chord just after the strum (≈ 0.5 s).
     * @param pattern six characters, low E to high e: "x" muted, "0" open, digits = fret.
     */
    fun verdicts(samples: FloatArray, sampleRate: Double, pattern: String): List<Verdict> {
        val expected = notes(pattern)
        // Muted strings are modelled at their open pitch — that's what they sound if they ring.
        val model = expected.mapIndexed { i, f -> f ?: openStrings[i] }
        val spectrum = Spectrum(samples, sampleRate)
        val close = { a: Double, b: Double -> abs(1200 * log2(a / b)) < 25 }

        // One measurement per overtone of every string; overtones that land on the same frequency are merged.
        val points = ArrayList<Double>()
        for (f in model) {
            for (k in 1..overtones) {
                val hz = f * k
                if (hz < 4000 && points.none { close(it, hz) }) points.add(hz)
            }
        }
        val measured = DoubleArray(points.size) { spectrum.peak(points[it], cents = 25.0) }
        fun shape(f: Double, weights: DoubleArray): DoubleArray = DoubleArray(points.size) { p ->
            val hz = points[p]
            val k = (hz / f).roundToInt()
            if (k >= 1 && k <= overtones && close(hz, f * k)) weights[k - 1] else 0.0
        }
        // Two columns per string (bright, mellow); a string's strength is the sum of its two gains.
        val templates = model.flatMap { f -> shapes.map { shape(f, it) } }.toMutableList()
        // How far each string's note stands above the room's rumble just beside it: a quarter-tone either side
        // (where no note sits), but at least 8 Hz away so it's clear of the note's own spread in a half-second window.
        val fundamentals = model.map { f ->
            val gap = max(f * 0.029, 8.0)
            val beside = (spectrum.peak(f - gap, cents = 10.0) + spectrum.peak(f + gap, cents = 10.0)) / 2
            max(0.0, spectrum.peak(f, cents = 25.0) - 2 * beside)
        }
        var strength = DoubleArray(model.size)
        // A ringing string always sounds its own note. When the fit credits a string with much more than its note
        // shows, it is explaining other strings' overtones (a muted low E "ringing" from the E3 and E4 in a C) — so
        // that string is dropped and the rest refitted.
        for (pass in 0 until 3) {
            val gains = nonNegativeLeastSquares(templates, measured)
            strength = DoubleArray(model.size) { i -> gains[2 * i] + gains[2 * i + 1] }
            val invented = model.indices.filter { i -> strength[i] > 0 && fundamentals[i] < strength[i] * fundamentalAtLeast }
            if (invented.isEmpty()) break
            for (i in invented) { templates[2 * i] = DoubleArray(points.size); templates[2 * i + 1] = templates[2 * i] }
        }
        val loudest = strength.maxOrNull() ?: 0.0
        if (loudest <= 0) return expected.map { if (it == null) Verdict.Muted else Verdict.Dead }

        // Muted strings that rang count as sounding too: their overtones can hide the strings above them.
        val ringing = expected.indices.filter { expected[it] == null && strength[it] / loudest > leaksAt }
        val sounding = expected.indices.map { i -> if (i in ringing) model[i] else expected[i] }
        return expected.indices.map { i ->
            if (expected[i] == null) return@map if (i in ringing) Verdict.Ringing else Verdict.Muted
            if (!identifiable(i, model, sounding, close)) return@map Verdict.Unclear
            if (strength[i] / loudest >= ringsAt) Verdict.Clear else Verdict.Dead
        }
    }

    /**
     * Whether string `i`'s own note is free of other strings' overtones. Only a lower string's overtone can land
     * on it (an octave or a twelfth above), and then a dead string and a ringing one sound almost the same.
     */
    private fun identifiable(i: Int, model: List<Double>, sounding: List<Double?>, close: (Double, Double) -> Boolean): Boolean =
        sounding.indices.none { j ->
            val f = sounding[j]
            if (j == i || f == null || f >= model[i] * 0.97) return@none false
            val k = (model[i] / f).roundToInt()
            k >= 2 && k <= overtones && close(model[i], f * k)
        }

    /** min ‖Σ gᵢ·tᵢ − y‖² with every gᵢ ≥ 0, by coordinate descent (six unknowns — converges in a few dozen sweeps). */
    internal fun nonNegativeLeastSquares(templates: List<DoubleArray>, y: DoubleArray): DoubleArray {
        val g = DoubleArray(templates.size)
        val residual = y.copyOf()
        val norms = templates.map { t -> t.sumOf { it * it } }
        for (sweep in 0 until 200) {
            var change = 0.0
            for (i in templates.indices) {
                if (norms[i] <= 0) continue
                val t = templates[i]
                var dot = 0.0
                for (b in t.indices) dot += t[b] * residual[b]
                val next = max(0.0, g[i] + dot / norms[i])
                val step = next - g[i]
                if (step != 0.0) {
                    for (b in t.indices) residual[b] -= step * t[b]
                    g[i] = next
                    change = max(change, abs(step))
                }
            }
            if (change < 1e-9) break
        }
        return g
    }
}

/** Magnitude spectrum of a short recording (Hann window, zero-padded 65 536-point FFT, ≈ 0.7 Hz per bin at 48 kHz). */
class Spectrum(samples: FloatArray, sampleRate: Double) {
    private val magnitudes: FloatArray
    private val binHz: Double

    init {
        val n = 1 shl 16
        val input = FloatArray(n)
        val count = min(samples.size, n)
        val window = RealFFT.hann(count)
        for (i in 0 until count) input[i] = samples[i] * window[i]
        magnitudes = RealFFT.magnitudes(input)
        binHz = sampleRate / n
    }

    /** The strongest bin within ±`cents` of `hz`. */
    fun peak(hz: Double, cents: Double): Double {
        val lo = max(1, (hz * 2.0.pow(-cents / 1200) / binHz).toInt())
        val hi = min(magnitudes.size - 1, ceil(hz * 2.0.pow(cents / 1200) / binHz).toInt())
        if (lo > hi) return 0.0
        var best = 0f
        for (b in lo..hi) if (magnitudes[b] > best) best = magnitudes[b]
        return best.toDouble()
    }
}

/**
 * Waits for a strum on the microphone and returns the half-second of chord just after it. Uses the shared
 * [MicInput]; call [next] from a coroutine on the main thread (it suspends until a strum, the timeout or [cancel]).
 */
class StrumListener {
    data class Strum(val samples: FloatArray, val sampleRate: Double)

    private val mic = MicInput(windowLength = 8192)
    /** Only touched on the mic's analysis coroutine. */
    private var waiting: ((FloatArray?) -> Unit)? = null
    private var noiseFloor = 0.002f
    private val captured = ArrayList<Float>()
    private var heardStrum = false
    private var skipped = 0
    private var warmedUp = 0
    private var sampleRate = 48000.0

    companion object {
        /** A strum is a jump to this many times the room's noise, and at least [minimumStrum]. */
        private const val jump = 3f
        private const val minimumStrum = 0.01f
        /** Skip the pick's click, then keep this much of the ringing chord. */
        private const val skipSeconds = 0.05
        private const val keepSeconds = 0.5
        /** The mic's first moments include a start-up click; spend them learning how loud the room is. */
        private const val warmUpSeconds = 0.3
    }

    init {
        mic.onWindow = { window, elapsed, rate ->
            sampleRate = rate
            val n = min(elapsed, window.size)
            take(window.copyOfRange(window.size - n, window.size))
        }
    }

    /** Listens until a strum (returns its audio) or `timeoutSeconds` (returns null). Throws [MicInput.Failure]. */
    suspend fun next(timeoutSeconds: Double = 6.0): Strum? {
        mic.start()
        try {
            val samples = withTimeoutOrNull((timeoutSeconds * 1000).toLong()) {
                suspendCancellableCoroutine<FloatArray?> { cont ->
                    mic.perform {
                        noiseFloor = 0.002f; captured.clear(); heardStrum = false; skipped = 0; warmedUp = 0
                        waiting = { s -> if (cont.isActive) cont.resume(s) }
                    }
                    cont.invokeOnCancellation { mic.perform { waiting = null } }
                }
            }
            return samples?.let { Strum(it, mic.sampleRate) }
        } finally {
            mic.perform { waiting = null }
            mic.stop()
        }
    }

    fun cancel() { mic.perform { resume(null) } }

    /** Frees the listener's background work. */
    fun close() = mic.close()

    /** On the analysis coroutine: track the room noise until the level jumps, then collect the chord. */
    private fun take(chunk: FloatArray) {
        if (waiting == null) return
        if (!heardStrum) {
            val level = MicInput.rms(chunk)
            if (warmedUp < (warmUpSeconds * sampleRate).toInt()) {
                warmedUp += chunk.size
                noiseFloor = if (warmedUp == chunk.size) level else (noiseFloor + level) / 2
                return
            }
            if (level > max(minimumStrum, noiseFloor * jump)) {
                heardStrum = true
            } else {
                // Falls quickly in a quiet moment but rises slowly, so talking or a fan doesn't drown out the strum.
                noiseFloor = if (level < noiseFloor) (noiseFloor + level) / 2 else noiseFloor * 0.98f + level * 0.02f
                return
            }
        }
        val skip = (skipSeconds * sampleRate).toInt()
        if (skipped < skip) {
            val drop = min(skip - skipped, chunk.size)
            skipped += drop
            for (i in drop until chunk.size) captured.add(chunk[i])
        } else {
            for (v in chunk) captured.add(v)
        }
        val keep = (keepSeconds * sampleRate).toInt()
        if (captured.size >= keep) resume(FloatArray(keep) { captured[it] })
    }

    private fun resume(samples: FloatArray?) {
        val w = waiting ?: return
        waiting = null
        w(samples)
    }
}
