package com.onezcodes.ochords

import android.view.KeyEvent
import com.onezcodes.ochords.services.NodGesture
import com.onezcodes.ochords.services.PedalKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandsFreeTest {
    @Test
    fun oneNodDownCountsOnce() {
        val g = NodGesture()
        val moves = ArrayList<NodGesture.Move>()
        // Resting, a quick turn of 0.3 rad, and back.
        val pitches = listOf(0.0, 0.0, 0.0, 0.1, 0.3, 0.3, 0.1, 0.0, 0.0, 0.0)
        for ((i, p) in pitches.withIndex()) g.hear(p, i * 0.066)?.let { moves.add(it) }
        assertEquals(listOf(NodGesture.Move.Down), moves)
    }

    @Test
    fun tiltUpIsTheOtherMoveAndRespectsTheRefractoryGap() {
        val g = NodGesture()
        val moves = ArrayList<NodGesture.Move>()
        val pitches = listOf(0.0, 0.0, -0.3, -0.3, 0.0, 0.0, -0.3, 0.0)
        for ((i, p) in pitches.withIndex()) g.hear(p, i * 0.066)?.let { moves.add(it) }
        // The second tilt comes back 0.26 s after the first move: too soon.
        assertEquals(listOf(NodGesture.Move.Up), moves)
    }

    @Test
    fun lookingDownForAWhileBecomesTheNewRest() {
        val g = NodGesture()
        var t = 0.0
        g.hear(0.0, t)
        // Head down for 1.5 s, then back up: not a nod, and the return isn't a tilt up either.
        var moves = 0
        while (t < 1.5) { t += 0.066; if (g.hear(0.3, t) != null) moves++ }
        t += 0.066
        if (g.hear(0.0, t) != null) moves++
        assertEquals(0, moves)
    }

    @Test
    fun learnsWhichWayIsDown() {
        val g = NodGesture()
        assertNull(g.learnDown(0.1, 0.0))
        assertNull(g.learnDown(0.12, 0.1))
        assertEquals(-1.0, g.learnDown(-0.2, 0.2)!!, 0.0)
    }

    @Test
    fun pedalKeysMapPressesAndHolds() {
        val p = PedalKeys()
        assertNull(p.handle(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN, 0, 1000))
        assertEquals(PedalKeys.Action.Next, p.handle(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP, 0, 1100))
        assertEquals(PedalKeys.Action.Back, p.handle(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.ACTION_DOWN, 0, 2000))
        assertNull(p.handle(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.ACTION_UP, 0, 2100))
        // Held: toggles once, and the release is not a page turn.
        assertNull(p.handle(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.ACTION_DOWN, 0, 3000))
        assertNull(p.handle(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.ACTION_DOWN, 1, 3300))
        assertEquals(PedalKeys.Action.ToggleScroll, p.handle(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.ACTION_DOWN, 2, 3700))
        assertNull(p.handle(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.ACTION_DOWN, 3, 3900))
        assertNull(p.handle(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.ACTION_UP, 0, 4000))
        assertNull(p.handle(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 0, 5000))
    }
}
