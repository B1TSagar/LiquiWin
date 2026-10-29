package io.github.b1tsagar.liquiwin.feature.start.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileSwipeTest {
    @Test fun aSwipeDownTurnsPastAThirdOfTheTileOrAsAFlick() {
        assertTrue(turnsDown(drag = 100f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 99f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertTrue(turnsDown(drag = 20f, velocity = 600f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 200f, velocity = -900f, heightPx = 300f, flingPx = 600f)) // flicked back up
    }

    @Test fun aSidewaysSwipeActsPastAThirdOfTheTileOrAsAFlick() {
        fun let(drag: Float, velocity: Float = 0f, canClear: Boolean = true) =
            sideways(drag, velocity, widthPx = 300f, flingPx = 600f, canClear = canClear)
        assertEquals(Sideways.CHAT, let(-100f))
        assertEquals(Sideways.CLEAR, let(100f))
        assertNull(let(-99f))
        assertNull(let(99f))
        assertEquals(Sideways.CHAT, let(-20f, velocity = -600f))
        assertEquals(Sideways.CLEAR, let(-150f, velocity = 900f)) // the flick decides, as on the pager
        assertNull(let(150f, canClear = false)) // a history page's messages only open
        assertEquals(Sideways.CHAT, let(-150f, canClear = false))
    }
}
