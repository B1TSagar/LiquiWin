package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test

class TiltTest {
    private val size = Size(200f, 100f)

    @Test fun centreTouchDepressesWithoutRotating() {
        val tilt = tiltFor(Offset(100f, 50f), size)
        assertEquals(0f, tilt.rotationXDeg, 0.01f)
        assertEquals(0f, tilt.rotationYDeg, 0.01f)
        assertEquals(1f, tilt.depression, 0.001f)
    }

    @Test fun edgeTouchRotatesByTheMaximumAngle() {
        val right = tiltFor(Offset(200f, 50f), size)
        val left = tiltFor(Offset(0f, 50f), size)
        assertEquals(17.19f, abs(right.rotationYDeg), 0.01f) // 0.3 rad
        assertEquals(-right.rotationYDeg, left.rotationYDeg, 0.01f)
        assertEquals(0f, right.rotationXDeg, 0.01f)
        assertEquals(0f, right.depression, 0.001f)
    }

    @Test fun touchesOutsideTheTileAreClamped() {
        val tilt = tiltFor(Offset(500f, -40f), size)
        assertEquals(17.19f, abs(tilt.rotationYDeg), 0.01f)
        assertEquals(17.19f, abs(tilt.rotationXDeg), 0.01f)
        assertEquals(0f, tilt.depression, 0.001f)
    }
}
