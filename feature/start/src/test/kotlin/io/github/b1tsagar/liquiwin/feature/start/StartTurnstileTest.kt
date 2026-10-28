package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import org.junit.Assert.assertEquals
import org.junit.Test

class StartTurnstileTest {
    @Test fun tilesGoColumnByColumnFromTheLeft() {
        val cells = mapOf(1L to Cell(0, 0), 2L to Cell(1, 2), 3L to Cell(2, 0), 4L to Cell(4, 4))
        assertEquals(mapOf(1L to 0, 2L to 0, 3L to 1, 4L to 2), startTurnstileOrder(cells, tapped = null))
    }

    @Test fun theTappedTileGoesLast() {
        val cells = mapOf(1L to Cell(0, 0), 2L to Cell(2, 0), 3L to Cell(4, 0))
        assertEquals(mapOf(1L to 3, 2L to 1, 3L to 2), startTurnstileOrder(cells, tapped = 1))
    }
}
