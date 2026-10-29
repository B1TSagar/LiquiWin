package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TilePagesTest {
    private val new = testItem("n", at = 20)
    private val old = testItem("o", at = 10, unread = false)
    private fun state(rotation: List<TileItem> = emptyList(), history: List<TileItem> = emptyList()) =
        TileState(rotation, rotation.size, history = history)

    @Test fun aSwipeDownTurnsToNewMessagesElseHistoryElseNothing() {
        assertEquals(TilePage.NEW, pageFor(TileSize.MEDIUM, state(listOf(new), listOf(old))))
        assertEquals(TilePage.HISTORY, pageFor(TileSize.WIDE, state(history = listOf(old))))
        assertNull(pageFor(TileSize.LARGE, state()))
        assertNull(pageFor(TileSize.SMALL, state(listOf(new))))
        assertNull(pageFor(TileSize.MEDIUM, null))
    }

    // Review Focus 4
    @Test fun aNewItemTurnsHistoryToNewMessages() {
        assertEquals(TilePage.NEW, settle(TilePage.HISTORY, TileSize.MEDIUM, state(listOf(new), listOf(old))))
        assertEquals(TilePage.HISTORY, settle(TilePage.HISTORY, TileSize.MEDIUM, state(history = listOf(old))))
        assertEquals(TilePage.NEW, settle(TilePage.NEW, TileSize.MEDIUM, state(listOf(new))))
    }

    // Review Focus 4
    @Test fun aPageWithNothingLeftGivesWayToTheFront() {
        assertNull(settle(TilePage.NEW, TileSize.WIDE, state(history = listOf(old)))) // all read or cleared
        assertNull(settle(TilePage.HISTORY, TileSize.WIDE, state())) // aged out
        assertNull(settle(TilePage.NEW, TileSize.SMALL, state(listOf(new)))) // made small
        assertNull(settle(null, TileSize.WIDE, state(listOf(new))))
    }

    @Test fun aPageTurnsThroughItsItemsWithoutTheFront() {
        val read = (7 downTo 1).map { testItem("i$it", at = it.toLong(), unread = false) }
        assertEquals(listOf("i7", "i6", "i5", "i4", "i3"), pageFaces(TileSize.MEDIUM, state(history = read), TilePage.HISTORY).map { it.key })
        assertEquals(listOf("history"), pageFaces(TileSize.LARGE, state(history = read), TilePage.HISTORY).map { it.key })
        assertEquals(listOf("stack"), pageFaces(TileSize.LARGE, state(listOf(new)), TilePage.NEW).map { it.key })
        assertEquals(listOf("front", "n"), pageFaces(TileSize.MEDIUM, state(listOf(new)), null).map { it.key })
        assertEquals(listOf("front"), pageFaces(TileSize.MEDIUM, state(), TilePage.NEW).map { it.key }) // until it settles
    }
}
