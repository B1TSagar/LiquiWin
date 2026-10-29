package io.github.b1tsagar.liquiwin.feature.start.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileSwipeTest {
    @Test fun aSwipeDownTurnsPastAThirdOfTheTileOrAsAFlick() {
        assertTrue(turnsDown(drag = 100f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 99f, velocity = 0f, heightPx = 300f, flingPx = 600f))
        assertTrue(turnsDown(drag = 20f, velocity = 600f, heightPx = 300f, flingPx = 600f))
        assertFalse(turnsDown(drag = 200f, velocity = -900f, heightPx = 300f, flingPx = 600f)) // flicked back up
    }
}
