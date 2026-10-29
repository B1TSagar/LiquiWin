package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class TileFacesTest {
    private val items = listOf(testItem("c", at = 30), testItem("b", at = 20), testItem("a", at = 10), testItem("z", at = 5))

    @Test fun smallTilesOnlyHaveTheirFront() {
        assertEquals(listOf(TileFace.Front), faces(TileSize.SMALL, items))
    }

    @Test fun mediumAndWideTilesHaveOneFaceForEachOfTheFiveNewest() {
        val seven = (7 downTo 1).map { testItem("m$it", at = it * 10L) }
        for (size in listOf(TileSize.MEDIUM, TileSize.WIDE)) {
            assertEquals(listOf("front", "c", "b", "a", "z"), faces(size, items).map { it.key })
            assertEquals(listOf("front", "m7", "m6", "m5", "m4", "m3"), faces(size, seven).map { it.key })
        }
    }

    // The owner's call: a large tile lists every unread message, to pick one to reply to
    @Test fun largeTilesListEveryItem() {
        assertEquals(listOf(TileFace.Front, TileFace.Stack(items)), faces(TileSize.LARGE, items))
    }

    @Test fun anEmptyRotationLeavesOnlyTheFront() {
        for (size in TileSize.entries) assertEquals(listOf(TileFace.Front), faces(size, emptyList()))
    }

    @Test fun nextFaceCyclesAndAGoneFaceGivesTheFront() {
        val faces = faces(TileSize.MEDIUM, items.take(2))
        assertEquals(listOf("c", "b", "front"), listOf("front", "c", "b").map { nextFace(faces, it).key })
        assertEquals("front", nextFace(faces, "read-meanwhile").key)
    }
}
