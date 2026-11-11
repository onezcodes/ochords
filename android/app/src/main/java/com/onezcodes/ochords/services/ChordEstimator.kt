package com.onezcodes.ochords.services

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimates the chords of music playing nearby (a speaker, the radio, a band): the chord now, the changes heard so
 * far, and at the end one chord per bar with the key, tempo and a capo suggestion. Pure logic — chromas and loudness
 * in, estimates out — so it can be tested on synthesised music. Not thread-safe: feed it from one thread.
 */
class ChordEstimator {
    /** A stretch of one chord, as heard live. */
    data class Segment(
        val chord: String,
        val start: Double,
        /** 0–100: how clearly the sound matched this chord. */
        val confidence: Int,
    )

    data class Bar(val chord: String, val confidence: Int)

    data class Summary(
        /** "C", "Am" …; empty until enough has been heard. */
        val key: String,
        val keyConfidence: Int,
        val bpm: Int?,
        /** The capo that makes the most chords open shapes (0: none needed). */
        val capo: Int,
        val bars: List<Bar>,
    )

    /** A 4-bar phrase group: bars `range` (indices into the summary's bars) labelled `letter` (A, B …). */
    data class Part(val letter: Char, val range: IntRange)

    companion object {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        /** Major, minor and dominant 7th on every root: what popular songs are mostly made of. */
        val names: List<String> = listOf("", "m", "7").flatMap { q -> roots.map { it + q } }
        private val templates: List<DoubleArray> = names.map { ChordRecognizer.template(it)!! }
        /**
         * A 7th chord holds the triad's notes (and a sung melody often touches the 7th), so it has to match clearly
         * better before it's named.
         */
        private val bias: DoubleArray = DoubleArray(names.size) { if (names[it].endsWith("7")) -0.06 else 0.0 }

        /** The least a sound must fit its best chord to count as one (music measures 0.84–0.96). */
        private const val chordLike = 0.8
        /** Seconds between chromas ([hear]). */
        const val frameInterval = 0.1
        /** Loudness readings per second ([hearLoudness]). */
        const val envelopeRate = 50.0
        /** Frames smoothed together for the live chord, and readings a new chord must lead for before it's shown. */
        private const val smoothing = 3
        private const val switchAfter = 2

        /**
         * The song's parts as heard: bars in 4-bar phrases, a phrase that repeats an earlier one (3 of 4 bars alike)
         * given the same letter, and neighbouring phrases with the same letter joined — A A B B A …
         */
        fun parts(bars: List<String>): List<Part> {
            val phrases = (0 until bars.size step 4).map { it until min(bars.size, it + 4) }
            val shapes = ArrayList<List<String>>()
            val parts = ArrayList<Part>()
            for (phrase in phrases) {
                val chords = bars.slice(phrase)
                val alike = shapes.indexOfFirst { shape ->
                    val same = shape.zip(chords).count { (a, b) -> a == b }
                    same >= min(shape.size, chords.size) - (if (chords.size >= 3) 1 else 0)
                }.let { if (it < 0) null else it }
                val index = alike ?: shapes.size
                if (alike == null) shapes.add(chords)
                val letter = ('A' + min(index, 25))
                val last = parts.lastOrNull()
                if (last != null && last.letter == letter) {
                    parts[parts.size - 1] = Part(letter, last.range.first..phrase.last)
                } else {
                    parts.add(Part(letter, phrase))
                }
            }
            return parts
        }

        /**
         * 0–100: how many readings picked this chord (a 7th and its triad half count for each other), and how well the
         * sound fit it.
         */
        private fun confidence(index: Int, scores: List<DoubleArray>): Int {
            val voiced = scores.filter { it.isNotEmpty() }
            if (voiced.isEmpty()) return 0
            val agree = voiced.sumOf { s ->
                val best = argmax(s)
                if (best == index) 1.0
                else if (best % 12 == index % 12 && (best / 12 != 1) == (index / 12 != 1)) 0.5
                else 0.0
            } / voiced.size
            val fit = voiced.sumOf { it[index] } / voiced.size
            val value = 0.6 * agree + 0.4 * min(1.0, max(0.0, (fit - 0.6) / 0.3))
            return (100 * value).roundToInt()
        }

        /**
         * The beat period with the strongest repeating onsets, 60–180 BPM, leaning towards ≈ 100 BPM so half and
         * double tempo don't win on a tie.
         */
        fun tempo(envelope: List<Double>): Double? {
            val onsets = onsets(envelope)
            if (onsets.size < (6 * envelopeRate).toInt()) return null
            val mean = onsets.sum() / onsets.size
            val o = DoubleArray(onsets.size) { onsets[it] - mean }
            val energy = o.sumOf { it * it }
            if (energy <= 0) return null
            val lags = (envelopeRate * 60 / 180).toInt()..(envelopeRate * 60 / 60).toInt()
            var bestLag = 0; var bestScore = Double.NEGATIVE_INFINITY; var bestRaw = 0.0
            val ac = DoubleArray(lags.last + 2)
            for (lag in lags.first - 1..lags.last + 1) {
                var s = 0.0
                for (i in lag until o.size) s += o[i] * o[i - lag]
                ac[lag] = s / energy
            }
            for (lag in lags) {
                val bpm = 60 * envelopeRate / lag
                val prior = exp(-0.5 * (log2(bpm / 100) / 0.9).pow(2))
                val score = ac[lag] * prior
                if (score > bestScore) { bestLag = lag; bestScore = score; bestRaw = ac[lag] }
            }
            if (bestRaw <= 0.1) return null
            // Between envelope samples: the peak of a parabola through the best lag and its neighbours.
            val a = ac[bestLag - 1]; val b = ac[bestLag]; val c = ac[bestLag + 1]
            val shift = if (a - 2 * b + c == 0.0) 0.0 else 0.5 * (a - c) / (a - 2 * b + c)
            return 60 * envelopeRate / (bestLag + max(-0.5, min(0.5, shift)))
        }

        /** Rises in loudness (strums, hits), on a log scale so quiet and loud passages count alike. */
        private fun onsets(envelope: List<Double>): DoubleArray =
            DoubleArray(max(0, envelope.size - 1)) { max(0.0, ln(envelope[it + 1] + 1e-4) - ln(envelope[it] + 1e-4)) }

        /**
         * When the last strum or hit was (s): the last onset at least half as strong as a typical strong one (the
         * first sound out of silence jumps far more than any strum after it, so not the strongest).
         */
        private fun lastOnset(envelope: List<Double>): Double {
            val o = onsets(envelope)
            val sorted = o.sorted()
            val levels = envelope.sorted()
            if (sorted.isEmpty()) return Double.POSITIVE_INFINITY
            val typical = sorted[((sorted.size - 1) * 0.95).toInt()]
            val loud = levels[((levels.size - 1) * 0.95).toInt()]
            // Flickers in a fading ring-out look like strums on a log scale: only a jump to a real playing level counts.
            if (typical <= 0) return Double.POSITIVE_INFINITY
            val last = o.indices.lastOrNull { o[it] >= typical / 2 && envelope[it + 1] >= loud * 0.3 } ?: return Double.POSITIVE_INFINITY
            return (last + 1) / envelopeRate
        }

        /** When in the beat the onsets fall: the offset (s) from the start whose beat grid catches the most onset. */
        private fun beatPhase(envelope: List<Double>, period: Double): Double {
            val o = onsets(envelope)
            val step = (period * envelopeRate).roundToInt()
            if (step <= 0 || o.size <= step) return 0.0
            fun caught(from: Int): Double { var s = 0.0; var i = from; while (i < o.size) { s += o[i]; i += step }; return s }
            val best = (0 until step).maxByOrNull { caught(it) } ?: 0
            // Onsets are between envelope samples i and i+1.
            return (best + 1) / envelopeRate
        }

        /** Chord changes falling on beat `offset` of each 4, minus those elsewhere: high when the bar lines are right. */
        private fun changes(labels: List<Int>, onBeat: Int): Int {
            var score = 0
            for (i in 1 until max(1, labels.size)) {
                if (labels[i] != labels[i - 1] && labels[i] >= 0 && labels[i - 1] >= 0) {
                    score += if ((i - onBeat) % 4 == 0) 1 else -1
                }
            }
            return score
        }

        private val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        private val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

        /** The key whose note profile (Krumhansl) best fits the chords heard, and how clearly it beats the next best. */
        fun key(chords: List<String>): Pair<String, Int> {
            val notes = DoubleArray(12)
            for (chord in chords) {
                val (root, intervals, _) = ChordRecognizer.parse(chord) ?: continue
                for ((i, interval) in intervals.withIndex()) notes[(root + interval) % 12] += if (i == 0) 1.5 else 1.0
            }
            if (notes.none { it > 0 }) return "" to 0
            val fits = ArrayList<Pair<String, Double>>()
            for (tonic in 0 until 12) {
                for ((profile, suffix) in listOf(majorProfile to "", minorProfile to "m")) {
                    val rotated = DoubleArray(12) { profile[(it - tonic + 12) % 12] }
                    fits.add((roots[tonic] + suffix) to correlation(notes, rotated))
                }
            }
            fits.sortByDescending { it.second }
            val margin = fits[0].second - fits[1].second
            return fits[0].first to min(99.0, max(30.0, 55 + margin * 300)).roundToInt()
        }

        /** Chords a beginner plays as open shapes. */
        private val openShapes = setOf("C", "D", "E", "F", "G", "A", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7")

        /**
         * The capo (0–7) that turns the most chords into open shapes — only for songs mostly in barre chords (a song in
         * C already plays open), and only if it helps at least two chords.
         */
        fun capo(chords: List<String>): Int {
            val unique = chords.filter { it.isNotEmpty() }.toSet()
            fun open(capo: Int): Int = unique.count { ChordTheory.shown(it, transpose = 0, capo = capo, easy = false).name in openShapes }
            if (open(0) >= unique.size * 0.6) return 0
            val best = (0..7).maxWithOrNull(compareBy<Int> { open(it) }.thenByDescending { it }) ?: 0
            return if (open(best) >= open(0) + 2) best else 0
        }

        private fun correlation(a: DoubleArray, b: DoubleArray): Double {
            val ma = a.sum() / 12; val mb = b.sum() / 12
            var num = 0.0; var da = 0.0; var db = 0.0
            for (i in 0 until 12) { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma) * (a[i] - ma); db += (b[i] - mb) * (b[i] - mb) }
            return if (da > 0 && db > 0) num / sqrt(da * db) else 0.0
        }

        private fun sum(rows: List<DoubleArray>): DoubleArray {
            val first = rows.firstOrNull() ?: return DoubleArray(0)
            val total = first.copyOf()
            for (row in rows.drop(1)) for (i in total.indices) total[i] += row[i]
            return total
        }

        private fun argmax(v: DoubleArray): Int {
            var best = 0
            for (i in v.indices) if (v[i] > v[best]) best = i
            return best
        }
        private fun argmaxOptional(v: DoubleArray): Int? = if (v.isEmpty()) null else argmax(v)
    }

    private class Frame(
        val time: Double,
        /** Match with each of [names]; empty while it's quiet. */
        val scores: DoubleArray,
    )

    private val frames = ArrayList<Frame>()
    private val envelope = ArrayList<Double>()
    private val segmentList = ArrayList<Segment>()
    /** The chords heard so far, oldest first (a snapshot). */
    val segments: List<Segment> get() = segmentList.toList()
    private var challenger: Pair<Int, Int>? = null // index, count
    private var segmentStart = 0

    /** Seconds of sound heard. */
    val heard: Double get() = frames.lastOrNull()?.let { it.time + frameInterval } ?: 0.0

    // Input

    /** A chroma every [frameInterval] (null while it's too quiet to read). */
    fun hear(chroma: DoubleArray?, time: Double) {
        var scores = if (chroma != null) DoubleArray(names.size) { ChordRecognizer.similarity(chroma, templates[it]) + bias[it] } else DoubleArray(0)
        // Room noise, talk and drums alone fit every chord a little (≈ 0.75) and none well: not a chord.
        if ((scores.maxOrNull() ?: 0.0) < chordLike) scores = DoubleArray(0)
        frames.add(Frame(time, scores))
        updateSegments()
    }

    /** The loudness (RMS) of the newest `1 / envelopeRate` s of sound. */
    fun hearLoudness(loudness: Double) { envelope.add(loudness) }

    // Live

    private fun updateSegments() {
        val recent = frames.takeLast(smoothing).filter { it.scores.isNotEmpty() }
        // The first chord only once the sound is steady, not on the edge of the first strum.
        if (recent.size < (if (segmentList.isEmpty()) smoothing else 2)) return
        val best = argmax(sum(recent.map { it.scores }))
        val current = segmentList.lastOrNull()
        val index = current?.let { names.indexOf(it.chord).takeIf { i -> i >= 0 } }
        if (current == null || index == null) {
            segmentStart = frames.size - recent.size
            segmentList.add(Segment(names[best], frames[segmentStart].time, 0))
            refreshConfidence()
            return
        }
        val c = challenger
        if (best == index) {
            challenger = null
        } else if (c != null && c.first == best) {
            challenger = best to c.second + 1
        } else {
            challenger = best to 1
        }
        challenger?.let { ch ->
            if (ch.second >= switchAfter) {
                challenger = null
                segmentStart = frames.size - switchAfter
                segmentList.add(Segment(names[ch.first], frames[segmentStart].time, 0))
            }
        }
        refreshConfidence()
    }

    private fun refreshConfidence() {
        val last = segmentList.lastOrNull() ?: return
        val index = names.indexOf(last.chord)
        if (index < 0) return
        segmentList[segmentList.size - 1] = last.copy(confidence = confidence(index, frames.subList(segmentStart, frames.size).map { it.scores }))
    }

    /** The chord most often heard right after `chord` so far — "next likely" while it's playing. */
    fun likelyNext(after: String): String? {
        val counts = HashMap<String, Int>()
        for (i in 0 until segmentList.size - 1) {
            if (segmentList[i].chord == after) counts[segmentList[i + 1].chord] = (counts[segmentList[i + 1].chord] ?: 0) + 1
        }
        return counts.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key
    }

    // Summary

    /** Beats per minute from the rhythm of the loudness (strums, drums), or null if there's no steady beat yet. */
    val bpm: Double? get() = tempo(envelope.takeLast((20 * envelopeRate).toInt()))

    /** One chord per bar of 4 beats, with key, tempo and capo. Quiet bars at either end are left out. */
    fun summary(): Summary {
        val bpm = this.bpm
        val beat = bpm?.let { 60 / it } ?: 0.5
        val phase = beatPhase(envelope, beat)
        // Each beat's chord, then the bar line where the chord changes most often.
        val beats = ArrayList<Pair<DoubleArray, Double>>() // scores, voiced
        var t = phase
        val heard = this.heard
        while (t < heard) {
            val inBeat = frames.filter { it.time >= t && it.time < t + beat }
            val voiced = inBeat.filter { it.scores.isNotEmpty() }
            beats.add((if (voiced.isEmpty()) DoubleArray(0) else sum(voiced.map { it.scores })) to (if (inBeat.isEmpty()) 0.0 else voiced.size.toDouble() / inBeat.size))
            t += beat
        }
        val labels = beats.map { if (it.first.isEmpty()) -1 else argmax(it.first) }
        val downbeat = (0 until 4).maxByOrNull { changes(labels, it) } ?: 0

        // Bars of 4 beats from the bar line; the beats before it (if any) are a pickup bar.
        val lastStrum = lastOnset(envelope)
        val bars = ArrayList<Bar>()
        var start = 0
        var end = min(beats.size, if (downbeat == 0) 4 else downbeat)
        while (start < beats.size) {
            val from = phase + start * beat
            val to = phase + end * beat
            // The last chord ringing on after the playing stops isn't another bar.
            if (from > lastStrum) break
            val span = beats.subList(start, end)
            val voiced = span.sumOf { it.second } / span.size
            val barFrames = frames.filter { it.time >= from && it.time < to }.map { it.scores }
            val index = if (voiced >= 0.3) argmaxOptional(sum(barFrames.filter { it.isNotEmpty() })) else null
            if (index != null) {
                bars.add(Bar(names[index], confidence(index, barFrames)))
            } else if (bars.isNotEmpty()) {
                bars.add(Bar("", 0))
            }
            start = end
            end = min(beats.size, end + 4)
        }
        while (bars.lastOrNull()?.chord == "") bars.removeAt(bars.size - 1)
        // A pickup on the same chord as the first full bar is part of it.
        if (downbeat != 0 && bars.size > 1 && bars[0].chord == bars[1].chord) bars.removeAt(0)
        // A quiet bar in the middle keeps the chord around it.
        for (i in bars.indices) if (bars[i].chord.isEmpty()) bars[i] = Bar(bars[i - 1].chord, min(50, bars[i - 1].confidence))

        val (key, keyConfidence) = key(bars.map { it.chord })
        return Summary(key, keyConfidence, bpm?.roundToInt(), capo(bars.map { it.chord }), bars)
    }
}
