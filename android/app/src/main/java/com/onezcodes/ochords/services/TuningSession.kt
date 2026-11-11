package com.onezcodes.ochords.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.log2

/**
 * The rules of a tuning session: which string the tuner is showing, how far off it is, and which strings are done.
 * Pure logic (time is passed in), so the "is it tuned?" behaviour can be tested without a microphone.
 *
 * The visible fields ([targets], [auto], [selected], [tuned], [cents], [heard]) are Compose state, so a screen can
 * keep one instance in `remember { }` and read them directly; feed it from the main thread.
 */
class TuningSession(targets: List<Double>, selected: Int = 1) {
    sealed class Event {
        /** Auto mode moved to another string. */
        data class Switched(val to: Int) : Event()
        /** A string came into tune (`all`: every string is now tuned). */
        data class Tuned(val string: Int, val all: Boolean) : Event()
        /** A tuned string was clearly turned out of tune again. */
        data class Untuned(val string: Int) : Event()
    }

    companion object {
        /** ±3 ¢ is inaudible to most players; it must hold briefly before a string counts as tuned. */
        const val tolerance = 3.0
        const val holdToTune = 0.4
        /**
         * The last reading stays live this long after the sound stops (bridges short gaps); after that it's shown
         * greyed as "pluck again", so nobody keeps turning a peg against a stale reading.
         */
        const val holdAfterNote = 0.3
        /**
         * Auto mode moves to another string only after this many readings in a row (≈ 80 ms) point to it, so one stray
         * reading — a neighbouring string still ringing — can't pull the tuner away.
         */
        const val readingsToSwitch = 3
        /** A reading this far from a string that's already tuned belongs to another string… */
        const val tunedStringReach = 20.0
        /**
         * …the nearest untuned one, if it's no more than this much further away than the tuned one (so a tuned string
         * re-plucked a little sharp stays itself, while a neighbour two semitones flat is recognised).
         */
        const val untunedMargin = 250.0
        /** A tuned string loses its tick only when it's clearly been turned away: this far off… */
        const val untuneCents = 12.0
        /** …for this long… */
        const val untuneAfter = 1.5
        /** …not counting the start of each pluck, which always rings a little sharp. */
        const val attack = 0.5
    }

    /** Each string's target pitch, low to high. */
    var targets: List<Double> by mutableStateOf(targets)
        private set
    var auto: Boolean by mutableStateOf(true)
    var selected: Int by mutableStateOf(selected)
        private set
    var tuned: Set<Int> by mutableStateOf(emptySet())
        private set
    /** Last offset of each string from its target, in cents (null = not played yet). */
    var cents: List<Double?> by mutableStateOf(List(targets.size) { null })
        private set
    /** The pitch being heard right now, smoothed (null = quiet). */
    var heard: Double? by mutableStateOf(null)
        private set

    private val recent = ArrayList<Double>()
    private var lastHeardAt: Double? = null
    private var inTuneSince: Double? = null
    private var outOfTuneSince: Double? = null
    /** When the current note started (after a gap or a switch). */
    private var noteStartedAt: Double? = null
    private var candidate: Pair<Int, Int>? = null // string, count

    val isPlaying: Boolean get() = heard != null
    val allTuned: Boolean get() = tuned.size == targets.size

    fun offset(hz: Double, from: Int): Double = 1200 * log2(hz / targets[from])

    /** New tuning or reference pitch: start over. */
    fun reset(targets: List<Double>) {
        this.targets = targets
        tuned = emptySet()
        cents = List(targets.size) { null }
        quiet()
    }

    /** The player picked a string (switches to manual mode). */
    fun select(string: Int) {
        auto = false
        selected = string
        quiet()
    }

    /** Demo / preview: tick strings without hearing them. */
    fun markTuned(strings: Set<Int>) { tuned = strings }

    /** Feeds one reading (null = nothing clear heard) at time `now` (seconds). */
    fun hear(hz: Double?, now: Double): List<Event> {
        if (hz == null) {
            val last = lastHeardAt
            if (last != null && now - last < holdAfterNote) return emptyList()
            quiet()
            return emptyList()
        }
        val events = ArrayList<Event>()
        if (heard == null) noteStartedAt = now
        lastHeardAt = now

        // Median of the last three readings (≈ 60 ms) drops the odd stray reading without lagging behind the peg.
        recent.add(hz)
        if (recent.size > 3) recent.removeAt(0)
        val smooth = recent.sorted()[recent.size / 2]
        heard = smooth

        if (auto) {
            val nearest = stringFor(hz)
            if (nearest != null) {
                if (nearest == selected) {
                    candidate = null
                } else {
                    val c = candidate
                    val count = if (c != null && c.first == nearest) c.second + 1 else 1
                    candidate = nearest to count
                    if (count < readingsToSwitch) return events
                    selected = nearest
                    candidate = null
                    recent.clear(); recent.add(hz)
                    heard = hz
                    inTuneSince = null
                    outOfTuneSince = null
                    noteStartedAt = now
                    events.add(Event.Switched(nearest))
                }
            }
        }

        val off = offset(heard!!, from = selected)
        cents = cents.toMutableList().also { it[selected] = off }

        if (abs(off) <= tolerance) {
            outOfTuneSince = null
            val since = inTuneSince ?: now
            inTuneSince = since
            if (now - since >= holdToTune && selected !in tuned) {
                tuned = tuned + selected
                events.add(Event.Tuned(selected, all = allTuned))
            }
        } else {
            inTuneSince = null
            val settled = now - (noteStartedAt ?: now) >= attack
            if (selected in tuned && settled && abs(off) > untuneCents) {
                val since = outOfTuneSince ?: now
                outOfTuneSince = since
                if (now - since >= untuneAfter) {
                    tuned = tuned - selected
                    outOfTuneSince = null
                    events.add(Event.Untuned(selected))
                }
            } else {
                outOfTuneSince = null
            }
        }
        return events
    }

    /**
     * The string a pitch most likely belongs to. Usually the nearest one — but a string far out of tune can sound
     * closer to its already-tuned neighbour (a B two semitones flat is as near G as B), and a tuned string sits
     * close to its note, so a pitch well away from a tuned string goes to the nearest untuned string instead.
     */
    private fun stringFor(hz: Double): Int? {
        val byDistance = targets.indices.sortedBy { abs(offset(hz, it)) }
        val nearest = byDistance.firstOrNull() ?: return null
        val distance = abs(offset(hz, nearest))
        if (nearest !in tuned || distance <= tunedStringReach) return nearest
        return byDistance.firstOrNull { it !in tuned && abs(offset(hz, it)) < distance + untunedMargin } ?: nearest
    }

    private fun quiet() {
        heard = null
        recent.clear()
        lastHeardAt = null
        inTuneSince = null
        outOfTuneSince = null
        noteStartedAt = null
        candidate = null
    }
}
