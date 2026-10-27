package io.github.b1tsagar.liquiwin.core.design.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WpUnitsTest {
    @Test fun wpPixelsScaleWithScreenWidth() {
        val units = WpUnits(screenWidthDp = 411.43f) // the Galaxy S24 at its 420 dpi setting
        assertEquals(411.43f, units.dp(480f).value, 0.01f)
        assertEquals(20.57f, units.dp(24f).value, 0.01f)
        assertEquals(61.71f, units.sp(72f).value, 0.01f)
    }
}
