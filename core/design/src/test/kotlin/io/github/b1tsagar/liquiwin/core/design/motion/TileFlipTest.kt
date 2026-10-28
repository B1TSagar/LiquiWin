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

    // §5.1: at any point in the turn, the nearer edge looks at most 5% wider than the farther one
    @Test fun aTileIsSeenFromFarEnoughThatItsNearerEdgeLooksAtMost5PercentWider() {
        for (height in listOf(150f, 638f, 1_400f)) {
            val cameraPx = tileFlipCameraDistance(height) * 72f // Skia's camera units
            val ratio = (cameraPx + height / 2) / (cameraPx - height / 2) // edge-on, where the widths differ most
            assertTrue("$height px tall: $ratio", ratio in 1.04f..1.05f)
        }
    }
}
