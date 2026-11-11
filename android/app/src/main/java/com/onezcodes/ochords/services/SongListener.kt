package com.onezcodes.ochords.services

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Listen: estimates the chords of the song playing nearby as it plays ([ChordEstimator]) from the microphone. The
 * sound is analysed on the phone and never recorded. Start, pause and finish on the main thread; [live] and [match]
 * are Compose state updated on the main thread.
 *
 * On iOS the same stream also names the song with ShazamKit. ShazamKit has no Android SDK, so [match] here only ever
 * goes from [Match.Searching] to [Match.NotFound] (after [searchFor] seconds of sound) — the screen's "not recognised"
 * path, where the song is named in Review. The chords work the same as on iOS.
 */
class SongListener {
    data class Live(
        val chord: String? = null,
        val confidence: Int = 0,
        val next: String? = null,
        /** The latest chord changes, oldest first. */
        val heard: List<ChordEstimator.Segment> = emptyList(),
        val seconds: Double = 0.0,
        val key: String = "",
        val keyConfidence: Int = 0,
        val bpm: Int? = null,
        val capo: Int = 0,
    )

    sealed class Match {
        object Searching : Match()
        data class Found(val title: String, val artist: String) : Match()
        /** The song wasn't recognised (always, on Android: there is no ShazamKit). */
        object NotFound : Match()
    }

    var live: Live by mutableStateOf(Live())
        private set
    var match: Match by mutableStateOf<Match>(Match.Searching)
        private set
    val isRunning: Boolean get() = mic.isRunning

    private val mic = MicInput(windowLength = ChordRecognizer.window)
    private val main = Handler(Looper.getMainLooper())
    /** Only touched on the mic's analysis coroutine. */
    private var analysis = Analysis()
    /** Seconds of sound the (absent) song identifier was fed; it gives up after [searchFor]. */
    private var identifiedFor = 0.0
    private var identifierDone = false

    companion object {
        /** Changes shown under "Heard so far". */
        private const val heardShown = 8
        /** How often the key, tempo and capo are worked out again (s). */
        private const val summaryEvery = 2.0
        /** Quieter than this (RMS) is silence. */
        private const val quietest = 0.002
        /** How long iOS's song identifier listens before giving up; kept so the screen's flow matches. */
        const val searchFor = 30.0
    }

    init {
        mic.onWindow = { window, elapsed, rate -> hear(window, elapsed, rate) }
    }

    /** Throws [MicInput.Failure]. */
    fun start() = mic.start()

    /** Stops listening; what's been heard is kept (start again to carry on). */
    fun pause() = mic.stop()

    /** Stops and gives the estimate for everything heard. */
    suspend fun finish(): ChordEstimator.Summary {
        mic.stop()
        identifierDone = true
        val done = CompletableDeferred<ChordEstimator.Summary>()
        mic.perform { done.complete(analysis.estimator.summary()) }
        return done.await()
    }

    /** Forgets everything heard, for a fresh start. */
    fun reset() {
        mic.stop()
        mic.perform { analysis = Analysis(); identifiedFor = 0.0; identifierDone = false }
        live = Live()
        match = Match.Searching
    }

    /** Frees the listener's background work. */
    fun close() = mic.close()

    // On the analysis coroutine

    private class Analysis {
        val estimator = ChordEstimator()
        /** Samples heard (while listening), for the estimator's clock. */
        var samples = 0L
        val pending = ArrayList<Float>()
        var nextFrame = 0.0
        var nextSummary = summaryEvery
        var summary: ChordEstimator.Summary? = null
    }

    private fun hear(window: FloatArray, count: Int, sampleRate: Double) {
        val freshCount = min(count, window.size)
        val freshFrom = window.size - freshCount
        feedIdentifier(freshCount, sampleRate)

        // Loudness every 20 ms of sound, for the beat.
        for (i in freshFrom until window.size) analysis.pending.add(window[i])
        val hop = (sampleRate / ChordEstimator.envelopeRate).toInt()
        while (analysis.pending.size >= hop) {
            var sum = 0.0
            for (i in 0 until hop) { val v = analysis.pending[i].toDouble(); sum += v * v }
            analysis.estimator.hearLoudness(sqrt(sum / hop))
            analysis.pending.subList(0, hop).clear()
        }
        analysis.samples += freshCount
        val now = analysis.samples / sampleRate

        // A chroma every 0.1 s of sound, for the chords.
        if (now < analysis.nextFrame) return
        val rms = MicInput.rms(window).toDouble()
        val chroma = if (rms >= quietest) ChordRecognizer.chroma(window, sampleRate) else null
        analysis.estimator.hear(chroma, analysis.nextFrame)
        analysis.nextFrame += ChordEstimator.frameInterval
        if (now >= analysis.nextSummary) {
            analysis.summary = analysis.estimator.summary()
            analysis.nextSummary = now + summaryEvery
        }

        val estimator = analysis.estimator
        val segments = estimator.segments
        val current = segments.lastOrNull()
        var next = Live(
            chord = current?.chord,
            confidence = current?.confidence ?: 0,
            next = current?.let { estimator.likelyNext(it.chord) },
            heard = segments.takeLast(heardShown),
            seconds = now,
        )
        analysis.summary?.let { s -> next = next.copy(key = s.key, keyConfidence = s.keyConfidence, bpm = s.bpm, capo = s.capo) }
        main.post { if (live != next) live = next }
    }

    /**
     * Where iOS feeds ShazamKit. There is no Android SDK for it, so the only outcome is "not recognised" once the
     * search time is up; a server-side fingerprint service could replace this later.
     */
    private fun feedIdentifier(samples: Int, sampleRate: Double) {
        if (identifierDone) return
        identifiedFor += samples / sampleRate
        if (identifiedFor > searchFor) {
            identifierDone = true
            main.post { match = Match.NotFound }
        }
    }
}
