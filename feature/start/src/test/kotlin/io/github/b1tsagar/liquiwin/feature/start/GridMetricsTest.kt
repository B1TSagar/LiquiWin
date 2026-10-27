package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GridMetricsTest {
    @Test fun galaxyS24WithThreeColumns() {
        val m = GridMetrics(screenWidthDp = 411.43f, units = 6, sideMarginWpPx = 24f, gapWpPx = 12f)
        assertEquals(53.14f, m.unitDp, 0.01f)
        assertEquals(63.43f, m.stepDp, 0.01f)
        assertEquals(116.57f, m.sizeDp(TileSize.MEDIUM).width.value, 0.01f)
    }
}
