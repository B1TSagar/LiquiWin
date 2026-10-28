package io.github.b1tsagar.liquiwin.core.design.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileFlipTest {
    @Test fun turnsHalfWayAtTheFaceSwapAndFullyAtTheEnd() {
        assertEquals(0f, tileFlipAngle(0f), 0.001f)
        assertEquals(180f, tileFlipAngle(850f), 0.001f)
        assertEquals(180f, tileFlipAngle(2_000f), 0.001f)
        assertTrue(tileFlipAngle(170f) < 90f && tileFlipAngle(200f) > 90f) // edge-on near the reference's 0.185 s swap
    }
}
