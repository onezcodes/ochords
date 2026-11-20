package com.onezcodes.ochords

import com.onezcodes.ochords.services.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxTest {

    @Test
    fun isEmptyWhenWidthOrHeightNonPositive() {
        assertTrue(Box.zero.isEmpty)
        assertTrue(Box(0.0, 0.0, 0.0, 1.0).isEmpty)
        assertTrue(Box(0.0, 0.0, 1.0, 0.0).isEmpty)
        assertFalse(Box(0.1, 0.2, 0.3, 0.4).isEmpty)
    }

    @Test
    fun unionSpansBothBoxes() {
        val a = Box(0.0, 0.0, 0.2, 0.1)
        val b = Box(0.3, 0.05, 0.2, 0.1)
        val u = a.union(b)
        assertEquals(0.0, u.x, 1e-9)
        assertEquals(0.0, u.y, 1e-9)
        assertEquals(0.5, u.width, 1e-9)
        assertEquals(0.15, u.height, 1e-9)
        assertEquals(b, Box.zero.union(b))
        assertEquals(a, a.union(Box.zero))
    }

    @Test
    fun intersectionAndDisjoint() {
        val a = Box(0.0, 0.0, 0.4, 0.4)
        val b = Box(0.2, 0.2, 0.4, 0.4)
        val i = a.intersection(b)
        assertEquals(0.2, i.x, 1e-9)
        assertEquals(0.2, i.y, 1e-9)
        assertEquals(0.2, i.width, 1e-9)
        assertEquals(0.2, i.height, 1e-9)

        val miss = a.intersection(Box(0.5, 0.5, 0.1, 0.1))
        assertTrue(miss.isEmpty)
    }

    @Test
    fun offsetMovesOrigin() {
        val b = Box(0.1, 0.2, 0.3, 0.4).offset(dx = 0.05, dy = -0.1)
        assertEquals(0.15, b.x, 1e-9)
        assertEquals(0.1, b.y, 1e-9)
        assertEquals(0.3, b.width, 1e-9)
        assertEquals(0.4, b.height, 1e-9)
    }
}
