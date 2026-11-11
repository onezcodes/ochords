package com.onezcodes.ochords.services

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Listens to the player strumming and follows them through a song's chords (see [ChordFollower]), ten readings a
 * second. Start and stop on the main thread; moves arrive on the main thread ([onMove] and the observable [current]).
 */
class ChordListener {
    /** The player moved on to this chord. Main thread. */
    var onMove: ((ChordFollower.Step) -> Unit)? = null
    /** The chord the player is on (null until the first chord is heard, or after a restart), for Compose. */
    var current: ChordFollower.Step? by mutableStateOf(null)
        private set

    private val mic = MicInput(windowLength = ChordRecognizer.window)
    private val main = Handler(Looper.getMainLooper())
    /** Only touched on the mic's analysis coroutine. */
    private var follower: ChordFollower? = null
    private var sinceReading = 0

    val isRunning: Boolean get() = mic.isRunning

    init {
        mic.onWindow = { window, elapsed, sampleRate -> read(window, elapsed, sampleRate) }
    }

    /**
     * @param steps the song's chords in play order, as they sound.
     * @param resumeAt item and line to wait for first (the player's place), or null for the song's first chord.
     * Throws [MicInput.Failure].
     */
    fun start(steps: List<ChordFollower.Step>, resumeAt: Pair<Int, Int?>? = null) {
        current = null
        mic.perform {
            val f = ChordFollower(steps)
            if (resumeAt != null) f.resync(resumeAt.first, resumeAt.second)
            follower = f
            sinceReading = 0
        }
        mic.start()
    }

    fun stop() = mic.stop()

    /** The player tapped a line or changed section: follow on from there. */
    fun resync(item: Int, line: Int?) {
        mic.perform { follower?.resync(item, line) }
    }

    /** Frees the listener's background work. */
    fun close() = mic.close()

    /** On the analysis coroutine. */
    private fun read(window: FloatArray, elapsed: Int, sampleRate: Double) {
        sinceReading += elapsed
        val hop = (sampleRate * readingSeconds).toInt()
        val f = follower
        if (sinceReading < hop || window.size < hop * 2 || f == null) return
        sinceReading = 0
        val level = MicInput.rms(window, window.size - hop, window.size)
        val moved = f.hear(ChordRecognizer.chroma(window, sampleRate), level)
        if (moved) {
            val step = f.current ?: return
            main.post {
                current = step
                onMove?.invoke(step)
            }
        }
    }

    private companion object {
        /** A reading every 0.1 s: chords change at most a few times a second, and each reading looks at 0.25 s of sound. */
        const val readingSeconds = 0.1
    }
}
