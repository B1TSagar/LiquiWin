package io.github.b1tsagar.liquiwin.core.tiles.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TileSizeTest {
    @Test fun nextCyclesThroughEverySize() {
        assertEquals(
            listOf(TileSize.MEDIUM, TileSize.WIDE, TileSize.LARGE, TileSize.SMALL),
            listOf(TileSize.SMALL, TileSize.MEDIUM, TileSize.WIDE, TileSize.LARGE).map { it.next() },
        )
    }
}
