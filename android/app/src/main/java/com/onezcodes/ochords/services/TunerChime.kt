package com.onezcodes.ochords.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Soft bell sounds for the tuner: a two-note "ding" when a string comes into tune and a rising arpeggio when all
 * six are. Synthesised on the fly, so there are no sound files to ship.
 *
 * Every note is above 1.3 kHz — out of the tuner's 60–1100 Hz range — and the tuner ignores the microphone while
 * a chime plays (pass the detector to [play], or call `PitchDetector.ignore(kind.duration)` yourself), so the bell
 * can't be mistaken for a string.
 */
class TunerChime {
    enum class Kind(
        /** (frequency, start in seconds) of each bell note: E6 → B6, then E6 G♯6 B6 E7. */
        internal val notes: List<Pair<Double, Double>>,
    ) {
        /** One string came into tune. */
        OneString(listOf(1318.5 to 0.0, 1975.5 to 0.09)),
        /** All six are. */
        AllStrings(listOf(1318.5 to 0.0, 1661.2 to 0.11, 1975.5 to 0.22, 2637.0 to 0.33));

        /** How long the sound lasts, including its ring-out. */
        val duration: Double get() = (notes.lastOrNull()?.second ?: 0.0) + ring

        companion object { internal const val ring = 0.55 }
    }

    private val tracks = HashMap<Kind, AudioTrack>()
    private var playing: AudioTrack? = null

    /**
     * Plays over the loudspeaker. Silently does nothing if audio isn't available — a missing ding shouldn't interrupt
     * tuning. With [detector], the microphone is ignored for the chime's duration.
     */
    fun play(kind: Kind, detector: PitchDetector? = null) {
        detector?.ignore(kind.duration)
        val track = tracks[kind] ?: run {
            val rendered = try { render(kind) } catch (_: Exception) { null } ?: return
            tracks[kind] = rendered
            rendered
        }
        playing?.let { if (it !== track) runCatching { it.stop() } }
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
            playing = track
        }
    }

    fun stop() {
        tracks.values.forEach { runCatching { it.stop() } }
        playing = null
    }

    /** Frees the audio tracks; the chime can't be played again. */
    fun close() {
        stop()
        tracks.values.forEach { it.release() }
        tracks.clear()
    }

    /** A bell note: the fundamental plus a quieter inharmonic partial, a 4 ms attack and an exponential ring-out. */
    private fun render(kind: Kind): AudioTrack? {
        val rate = SAMPLE_RATE
        val frames = (kind.duration * rate).toInt()
        val out = FloatArray(frames)
        for ((hz, at) in kind.notes) {
            val start = (at * rate).toInt()
            for (i in start until frames) {
                val t = (i - start) / rate
                val envelope = min(1.0, t / 0.004) * exp(-t * 7)
                val tone = sin(2 * PI * hz * t) + 0.25 * sin(2 * PI * hz * 2.76 * t) * exp(-t * 10)
                out[i] += (0.22 * envelope * tone).toFloat()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate.toInt())
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 4)
            .build()
        if (track.state != AudioTrack.STATE_NO_STATIC_DATA && track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        if (track.write(out, 0, frames, AudioTrack.WRITE_BLOCKING) < frames) {
            track.release()
            return null
        }
        track.setVolume(0.7f)
        return track
    }

    private companion object { const val SAMPLE_RATE = 44100.0 }
}
