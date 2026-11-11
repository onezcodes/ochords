package com.onezcodes.ochords.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The microphone, delivered as the newest window of sound every 20 ms on a background (analysis) coroutine — shared
 * by the tuner, Follow me, Listen and Chord check. Start and stop on the main thread.
 *
 * Audio comes from one shared [AudioRecord] (an UNPROCESSED or VOICE_RECOGNITION source, for a flat response with
 * no gain control) read by a high-priority thread into a lock-free ring buffer; every `MicInput` reads the ring on
 * its own 20 ms timer, so several consumers can listen at once. The hardware is opened when the first consumer
 * starts and closed when the last one stops (reference counted).
 *
 * Usage from a screen: hold one instance (`remember { PitchDetector() }` etc. wrap one), request RECORD_AUDIO at
 * runtime, then [start]; [stop] when the screen leaves. Sound never leaves the phone.
 */
class MicInput(
    /** Samples handed to [onWindow] each tick (the newest ones). At most half the ring. */
    val windowLength: Int,
) {
    /** Why the microphone couldn't start; `message` is user-facing (see `Throwable.userMessage`). */
    class Failure(val kind: Kind) : Exception(kind.message) {
        enum class Kind(val message: String) {
            NoMicrophoneAccess("Microphone access is off. Allow it in Settings to use this."),
            AudioUnavailable("The microphone isn’t available right now."),
        }
    }

    /**
     * Called on the analysis coroutine with the newest [windowLength] samples (fewer just after starting), how many
     * samples arrived since the last call, and the sample rate. The array is reused between calls: copy what you keep.
     */
    var onWindow: ((window: FloatArray, elapsed: Int, sampleRate: Double) -> Unit)? = null
    /** Called on the analysis coroutine instead of [onWindow] while the app's own sound is being ignored. */
    var onIgnored: (() -> Unit)? = null

    /** The capture rate in Hz (48 000 or 44 100); 0 until the first start. */
    val sampleRate: Double get() = MicSource.sampleRate

    @Volatile var isRunning = false
        private set

    private val analysis = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + analysis)
    private var timer: Job? = null
    /** Only touched on `analysis`. */
    private var window = FloatArray(windowLength)
    private var lastWritten = 0L
    private var ignoreUntil = 0L

    init {
        require(windowLength <= MicSource.capacity / 2) { "windowLength must be at most ${MicSource.capacity / 2}" }
    }

    /** Opens the microphone (shared) and starts the 20 ms windows. Throws [Failure]. */
    fun start() {
        if (isRunning) return
        MicSource.acquire()
        isRunning = true
        timer = scope.launch {
            lastWritten = MicSource.written
            var next = System.nanoTime()
            while (isActive) {
                next += INTERVAL_MS * 1_000_000
                val wait = (next - System.nanoTime()) / 1_000_000
                delay(if (wait > 0) wait else 1)
                tick()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        timer?.cancel()
        timer = null
        MicSource.release()
        scope.launch { lastWritten = 0; ignoreUntil = 0 }
    }

    /** Runs `work` on the analysis coroutine, in order with the windows (for state the consumer keeps there). */
    fun perform(work: () -> Unit) { scope.launch { work() } }

    /**
     * Skips the sound for `seconds` — while the app plays its own sound — plus long enough for it to leave the
     * window, so it's never analysed.
     */
    fun ignore(seconds: Double) {
        scope.launch {
            ignoreUntil = MicSource.written + ((seconds + 0.2) * MicSource.sampleRate).toLong() + windowLength
        }
    }

    /** A copy of the newest `n` samples (oldest first; fewer if less has been heard). Any thread. */
    fun latest(n: Int = windowLength): FloatArray = MicSource.latest(n)

    /** Loudness (RMS) of the newest `n` samples. Any thread. */
    fun rms(n: Int = windowLength): Float = rms(latest(n))

    /** Frees the analysis coroutine; the instance can't be started again. */
    fun close() {
        stop()
        scope.cancel()
    }

    /** On the analysis coroutine, every 20 ms. */
    private fun tick() {
        val written = MicSource.written
        val elapsed = (written - lastWritten).toInt()
        if (elapsed < MINIMUM_NEW_SAMPLES) return
        lastWritten = written
        if (written < ignoreUntil) {
            onIgnored?.invoke()
            return
        }
        val n = min(written, windowLength.toLong()).toInt()
        if (window.size != n) window = FloatArray(n)
        MicSource.latest(n, window)
        onWindow?.invoke(window, elapsed, MicSource.sampleRate)
    }

    companion object {
        private const val INTERVAL_MS = 20L
        /** A window only when at least ≈ 10 ms of new sound has arrived. */
        private const val MINIMUM_NEW_SAMPLES = 480

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Loudness (RMS) of a stretch of samples. */
        fun rms(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
            if (to <= from) return 0f
            var sum = 0.0
            for (i in from until to) sum += (samples[i] * samples[i]).toDouble()
            return sqrt(sum / (to - from)).toFloat()
        }
    }
}

/**
 * The one microphone: a reader thread copies [AudioRecord] frames into a single-writer ring of the latest samples,
 * which any number of [MicInput]s read. The ring holds at least two full windows, so a read can't be overtaken by the
 * writer in the few microseconds it takes.
 */
private object MicSource {
    const val capacity = 1 shl 15
    private val storage = FloatArray(capacity)

    /** Samples written since the microphone was first opened (a volatile store publishes each chunk). */
    @Volatile var written = 0L
        private set
    @Volatile var sampleRate = 0.0
        private set

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var users = 0

    @Synchronized
    fun acquire() {
        if (users == 0) {
            try {
                open()
            } catch (e: Throwable) {
                close()
                throw e
            }
        }
        users += 1
    }

    @Synchronized
    fun release() {
        if (users == 0) return
        users -= 1
        if (users == 0) close()
    }

    /** Copies the newest `n` samples into `out` (its first `n` slots), oldest first. */
    fun latest(n: Int, out: FloatArray) {
        val end = written
        val count = min(n.toLong(), min(end, capacity.toLong())).toInt()
        val start = end - count
        for (i in 0 until count) out[i] = storage[((start + i) and (capacity - 1).toLong()).toInt()]
    }

    fun latest(n: Int): FloatArray {
        val count = min(n.toLong(), min(written, capacity.toLong())).toInt()
        return FloatArray(count).also { latest(count, it) }
    }

    private fun open() {
        val rec = create() ?: throw MicInput.Failure(MicInput.Failure.Kind.AudioUnavailable)
        record = rec
        sampleRate = rec.sampleRate.toDouble()
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicInput.Failure(MicInput.Failure.Kind.AudioUnavailable)
        val t = Thread({ read(rec) }, "ochords.mic")
        t.priority = Thread.MAX_PRIORITY
        thread = t
        t.start()
    }

    private fun close() {
        val t = thread
        thread = null
        val rec = record
        record = null
        try { rec?.stop() } catch (_: IllegalStateException) {}
        t?.join(500)
        rec?.release()
    }

    /** 48 kHz when the device offers it, else 44.1; an unprocessed source when it exists, else voice recognition. */
    private fun create(): AudioRecord? {
        val sources = listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (rate in intArrayOf(48_000, 44_100)) {
            val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minBytes <= 0) continue
            for (source in sources) {
                val rec = try {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(minBytes, rate * 4 / 5)) // ≥ 0.2 s, so a busy moment loses nothing
                        .build()
                } catch (e: SecurityException) {
                    throw MicInput.Failure(MicInput.Failure.Kind.NoMicrophoneAccess)
                } catch (_: Exception) {
                    null
                }
                if (rec == null) continue
                if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
                rec.release()
            }
        }
        return null
    }

    /** The reader thread: blocks on ≈ 10 ms of frames at a time and copies them into the ring, no locks or allocation. */
    private fun read(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = FloatArray(480)
        while (record === rec) {
            val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (n <= 0) {
                if (n < 0) break
                continue
            }
            val start = written
            for (i in 0 until n) storage[((start + i) and (capacity - 1).toLong()).toInt()] = chunk[i]
            written = start + n
        }
    }
}
