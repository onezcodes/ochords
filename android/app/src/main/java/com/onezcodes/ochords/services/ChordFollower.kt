package com.onezcodes.ochords.services

import com.onezcodes.ochords.model.ChordLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radix-2 FFT of real input (plain Kotlin, in place of vDSP): returns the magnitude of bins 0 until n/2.
 * Twiddles are cached per size; `n` must be a power of two.
 */
internal object RealFFT {
    private val twiddles = HashMap<Int, Pair<DoubleArray, DoubleArray>>()

    @Synchronized
    private fun twiddle(n: Int): Pair<DoubleArray, DoubleArray> = twiddles.getOrPut(n) {
        val c = DoubleArray(n / 2)
        val s = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            val a = -2 * PI * i / n
            c[i] = cos(a); s[i] = sin(a)
        }
        c to s
    }

    /** Magnitudes |X[k]| for k in 0 until n/2 of the real signal `input` (its length is the FFT size). */
    fun magnitudes(input: FloatArray): FloatArray {
        val n = input.size
        require(n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        val re = DoubleArray(n) { input[it].toDouble() }
        val im = DoubleArray(n)
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                val t = re[i]; re[i] = re[j]; re[j] = t
            }
        }
        val (cs, sn) = twiddle(n)
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var start = 0
            while (start < n) {
                var k = 0
                for (i in start until start + half) {
                    val wr = cs[k]; val wi = sn[k]
                    val xr = re[i + half]; val xi = im[i + half]
                    val tr = xr * wr - xi * wi
                    val ti = xr * wi + xi * wr
                    re[i + half] = re[i] - tr; im[i + half] = im[i] - ti
                    re[i] += tr; im[i] += ti
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
        val half = n / 2
        return FloatArray(half) { sqrt(re[it] * re[it] + im[it] * im[it]).toFloat() }
    }

    /** `count` Hann window values (0.5·(1 − cos 2πn/N), as vDSP's). */
    fun hann(count: Int): FloatArray = FloatArray(count) { (0.5 * (1 - cos(2 * PI * it / count))).toFloat() }
}

/**
 * Which of a few known chords is sounding. Works on a "chroma" — how much of each of the 12 notes is in the sound —
 * compared with each chord's expected chroma. The expected chroma includes a guitar string's overtones (the 3rd
 * harmonic of C is a G, the 5th an E), so the overtones in a real strum count for the right chord instead of
 * blurring major and minor.
 */
object ChordRecognizer {
    /**
     * Samples analysed per reading: ≈ 0.25 s at 48 kHz — long enough to tell neighbouring notes apart in the bass,
     * short enough to follow a change of chord.
     */
    const val window = 12288
    private const val fftSize = 16384
    /** Overtones per note in a chord's expected chroma, and how fast they fade. */
    private const val overtones = 6
    private const val overtoneFade = 0.7

    /** The 12-note profile of a window of sound (C = 0 … B = 11), normalised to length 1; null if it's silent. */
    fun chroma(samples: FloatArray, sampleRate: Double): DoubleArray? {
        val n = min(samples.size, fftSize)
        if (n <= 1024) return null
        val input = FloatArray(fftSize)
        val hann = RealFFT.hann(n)
        val offset = samples.size - n
        for (i in 0 until n) input[i] = samples[offset + i] * hann[i]
        val mags = RealFFT.magnitudes(input)
        val half = fftSize / 2

        // Spectral peaks from low E (≈ 75 Hz) to ≈ 2 kHz, each credited to its nearest note. Only peaks count, so
        // the noise floor between notes doesn't smear into every note; square-root compression keeps one loud
        // string from drowning the rest of the chord.
        val binHz = sampleRate / fftSize
        val lo = (75 / binHz).toInt()
        val hi = min(half - 2, (2000 / binHz).toInt())
        if (lo <= 1 || hi <= lo) return null
        val chroma = DoubleArray(12)
        var top = 0f
        for (b in lo..hi) if (mags[b] > top) top = mags[b]
        val floor = top * 0.02f
        for (b in lo..hi) {
            if (mags[b] > floor && mags[b] >= mags[b - 1] && mags[b] >= mags[b + 1]) {
                val hz = b * binHz
                val note = (12 * log2(hz / 440)).roundToInt() + 9
                chroma[((note % 12) + 12) % 12] += sqrt(mags[b].toDouble())
            }
        }
        return normalised(chroma)
    }

    /** The chroma a guitar playing `chord` would show (null for a name that isn't a chord). */
    fun template(chord: String): DoubleArray? {
        val (root, intervals, bass) = parse(chord) ?: return null
        val t = DoubleArray(12)
        val notes = intervals.map { (root + it) % 12 }.toMutableList()
        if (bass != null) notes.add(bass)
        for ((i, note) in notes.withIndex()) {
            // The bass of a slash chord rings as strongly as the root; added colour notes a little less.
            val weight = if (i == 0 || (bass != null && i == notes.size - 1)) 1.0 else if (i < 3) 0.9 else 0.6
            for (k in 1..overtones) {
                val shift = (12 * log2(k.toDouble())).roundToInt()
                t[(note + shift) % 12] += weight * overtoneFade.pow(k - 1)
            }
        }
        return normalised(t)
    }

    /** How alike two chromas are: 1 = the same. */
    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in 0 until min(a.size, b.size)) s += a[i] * b[i]
        return s
    }

    /** Root note (0–11), intervals above it, and slash bass note, from a chord name. */
    data class Parsed(val root: Int, val intervals: List<Int>, val bass: Int?)

    fun parse(chord: String): Parsed? {
        val parts = chord.split("/", limit = 2)
        val main = parts.firstOrNull() ?: return null
        val root = note(main) ?: return null
        val hasAccidental = main.drop(1).firstOrNull()?.let { it == '#' || it == 'b' } == true
        val quality = main.drop(if (hasAccidental) 2 else 1)
        val bass = if (parts.size > 1) note(parts[1]) else null
        return Parsed(root, intervals(quality), bass)
    }

    private fun note(name: String): Int? {
        val first = name.firstOrNull() ?: return null
        if (first !in "ABCDEFG") return null
        var key = first.toString()
        val acc = name.drop(1).firstOrNull()
        if (acc == '#' || acc == 'b') key += acc
        return ChordTheory.index[key]
    }

    /** Chord tones (semitones above the root) for a chord quality ("", "m", "7", "maj7", "sus4", "m7b5" …). */
    fun intervals(q: String): List<Int> {
        val minor = q.startsWith("m") && !q.startsWith("maj")
        var tones = mutableListOf(0, if (minor) 3 else 4, 7)
        if (q.contains("sus2")) tones[1] = 2 else if (q.contains("sus")) tones[1] = 5
        if (q.contains("dim") || q.contains("b5") || q.contains("°") || q.contains("ø")) { tones[1] = 3; tones[2] = 6 }
        if (q.contains("aug") || q.contains("+") || q.contains("#5")) tones[2] = 8
        if (q == "5") tones = mutableListOf(0, 7)
        if (q.contains("maj7") || q.contains("M7")) tones.add(11)
        else if (q.contains("dim7") || q.contains("°7")) tones.add(9)
        else if (q.contains("7") || q.contains("9") || q.contains("11") || q.contains("13") || q.contains("ø")) tones.add(10)
        if (q.contains("6")) tones.add(9)
        if (q.contains("9") || q.contains("add2")) tones.add(2)
        return tones
    }

    private fun normalised(v: DoubleArray): DoubleArray? {
        val length = sqrt(v.sumOf { it * it })
        return if (length > 0) DoubleArray(v.size) { v[it] / length } else null
    }
}

/**
 * Follows a player through a song's chords: listens for the next chord in the song and moves on when it's clearly
 * being played. Only ever looks a couple of chords ahead, so repeated progressions (C G Am F, C G Am F…) are
 * followed in order, and a missed or skipped chord is caught up. Pure logic: chromas in, positions out.
 * Not thread-safe: use from one thread (the listener's analysis coroutine).
 */
class ChordFollower(steps: List<Step>) {
    data class Step(
        /** The chord as it sounds (after any transposition). */
        val chord: String,
        /** Where it is in the song: which stage item, and which lyric line or instrumental bar. */
        val item: Int,
        val line: Int? = null,
        val bar: Int? = null,
        /** Which time through a repeated line ("( … ) x2"): pass 1 of 2, 2 of 2. */
        val pass: Int = 1,
        val passes: Int = 1,
    )

    /** One play of a lyric line: `line` on its `pass` of `passes`. */
    data class Play(val line: Int, val pass: Int, val passes: Int)

    companion object {
        /**
         * The order a section's lines are played in, honouring repeat marks written in the lyrics:
         * "…lyrics… x2" or "( … ) x 2" at the end of a line repeats that line; a "(" opening one line and ") x2" closing
         * a later one repeats the lines in between. Also "×2", "(x2)", "2x". A ")" without a count just closes the group.
         */
        fun playOrder(lines: List<String>): List<Play> {
            val texts = lines.map { line ->
                ChordLine.parse(line).joinToString("") { it.text + (if (it.trailingSpace) " " else "") }.trim(' ', '\t')
            }
            val out = ArrayList<Play>()
            fun play(range: IntRange, times: Int) {
                for (pass in 1..times) for (l in range) out.add(Play(l, pass, times))
            }
            var i = 0
            while (i < texts.size) {
                // A group opened here and closed (with a count) on this or a later line.
                if (texts[i].startsWith("(") || texts[i].startsWith("（")) {
                    var j = i
                    var closed: Pair<Int, Int>? = null // end, times
                    while (j < texts.size && j - i < 12) {
                        val n = repeatCount(texts[j])
                        if (n != null) { closed = j to n; break }
                        // Closed without a count: just a bracketed aside.
                        if (texts[j].contains(")") || texts[j].contains("）")) break
                        j += 1
                    }
                    if (closed != null) {
                        play(i..closed.first, closed.second)
                        i = closed.first + 1
                        continue
                    }
                }
                play(i..i, repeatCount(texts[i]) ?: 1)
                i += 1
            }
            return out
        }

        private val repeatPatterns = listOf(
            Regex("""(?:^|[\s)\]）])[(\[]?\s*[x×]\s*([2-9])\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|[\s)\]）])[(\[]?\s*([2-9])\s*(?:x|×|times)\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE),
        )

        /** The repeat count written at the end of a line ("x2", "x 2", "×2", "(x2)", "2x", "2 times"), if any (2–9). */
        fun repeatCount(text: String): Int? {
            val t = text.trim(' ', '\t')
            for (p in repeatPatterns) {
                val m = p.find(t) ?: continue
                m.groupValues[1].toIntOrNull()?.let { return it }
            }
            return null
        }

        /** A chord must beat the one being played by this much… */
        const val margin = 0.04
        /** …and resemble a chord at all this much… */
        const val minimumMatch = 0.6
        /** …in this many readings in a row (≈ 0.2 s) to move on one chord; skipping one takes longer. */
        const val readingsToAdvance = 2
        const val readingsToSkip = 4
        /**
         * Skipping a chord needs a strong match: a wrong chord played in its place often resembles the one after it
         * (Dm for F), and a false skip is worse than a late one — the next strum of the right chord catches up.
         */
        const val skipMatch = 0.85
        /** A reading counts when it's this much louder than the room (the quietest moment of the last few seconds)… */
        const val aboveRoom = 2.5f
        /** …and at least this loud, but never has to be louder than [loudestGate], however noisy the room. */
        const val quietest = 0.003f
        const val loudestGate = 0.01f
        /** Readings the room level is taken over (≈ 3 s). */
        private const val roomMemory = 30
        /**
         * Chords this alike differ by one note (C → Cmaj7, Am → Am7, G → G/B): a strum can't reliably tell them apart,
         * so a change between them is followed as one chord. Genuinely different chords score 0.77 or less.
         */
        const val indistinct = 0.9
    }

    /**
     * Every chord in play order. A chord repeated back-to-back — or followed by one only a note different — can't be
     * heard changing, so it's folded into the first.
     */
    val steps: List<Step>
    /** The chord being played (-1 = not started: waiting for the first chord). */
    var position = -1
        private set
    private val templates: Map<String, DoubleArray>
    private var pending: Pair<Int, Int>? = null // target, count
    private val levels = ArrayDeque<Float>()

    init {
        val t = HashMap<String, DoubleArray>()
        for (s in steps) if (s.chord !in t) ChordRecognizer.template(s.chord)?.let { t[s.chord] = it }
        val folded = ArrayList<Step>()
        for (s in steps) {
            val last = folded.lastOrNull()
            if (last != null) {
                if (last.chord == s.chord) continue
                val a = t[last.chord]; val b = t[s.chord]
                if (a != null && b != null && ChordRecognizer.similarity(a, b) > indistinct) continue
            }
            folded.add(s)
        }
        this.steps = folded
        templates = t
    }

    val current: Step? get() = steps.getOrNull(position)

    /**
     * The player tapped a line (or moved to a section): wait for its first chord, looking from where they are so a
     * repeated section resolves to the next time it comes round.
     */
    fun resync(item: Int, line: Int?) {
        val matches = { s: Step -> s.item == item && (line == null || s.line == line) }
        val from = max(0, position)
        // Wait for that line's first chord (as if the chord before it were being played).
        var i = -1
        for (k in from until steps.size) if (matches(steps[k])) { i = k; break }
        if (i < 0) i = steps.indexOfFirst(matches)
        if (i >= 0) position = i - 1
        pending = null
    }

    /** Feeds one reading. Returns true when the position moved. */
    fun hear(chroma: DoubleArray?, level: Float): Boolean {
        levels.addLast(level)
        while (levels.size > roomMemory) levels.removeFirst()
        val room = levels.minOrNull() ?: 0f
        val gate = max(quietest, min(loudestGate, room * aboveRoom))
        if (chroma == null || level < gate || steps.isEmpty()) return false
        fun score(i: Int): Double {
            val t = steps.getOrNull(i)?.let { templates[it.chord] } ?: return -1.0
            return ChordRecognizer.similarity(chroma, t)
        }
        // Not started: the song's first chord — or the first sung line's, for a player who skips the intro. Starting
        // anywhere else is what tapping a line is for (a guess here can put the player a whole round out).
        val firstLine = steps.indexOfFirst { it.line != null }.let { if (it < 0) 0 else it }
        val ahead = if (position < 0) setOf(0, firstLine).sorted() else listOf(position + 1, position + 2)
        val here = if (position < 0) -1.0 else score(position)
        val best = ahead.filter { it in steps.indices }.maxByOrNull { score(it) }
        if (best == null || score(best) < minimumMatch || score(best) < here + margin) {
            pending = null
            return false
        }
        val p = pending
        val count = if (p != null && p.first == best) p.second + 1 else 1
        pending = best to count
        val skipping = position >= 0 && best > position + 1
        if (skipping && score(best) < skipMatch) {
            pending = null
            return false
        }
        if (count < (if (skipping) readingsToSkip else readingsToAdvance)) return false
        position = best
        pending = null
        return true
    }
}
