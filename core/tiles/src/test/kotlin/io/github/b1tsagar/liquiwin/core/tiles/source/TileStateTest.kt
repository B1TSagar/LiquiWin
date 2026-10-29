package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TileStateTest {
    private val a = FakeTileSource(SourceId("app:a@0"))
    private val b = FakeTileSource(SourceId("app:b@0"))

    // A large tile lists them all (§5.1), so the cap of five is the medium and wide faces'.
    @Test fun theRotationIsEveryUnreadItemNewestFirst() = runTest {
        a.itemState.value = listOf(testItem("read", at = 99, unread = false, source = a.id)) +
            (7 downTo 1).map { testItem("m$it", at = it * 10L, source = a.id) }
        val state = tileState(listOf(a)).first()
        assertEquals(listOf("m7", "m6", "m5", "m4", "m3", "m2", "m1"), state.rotation.map { it.key })
        assertEquals(7, state.unread)
    }

    @Test fun twoSourcesInterleaveByTimeAndAddUpTheirCounts() = runTest {
        a.itemState.value = listOf(testItem("a2", at = 30, source = a.id), testItem("a1", at = 10, source = a.id))
        b.itemState.value = listOf(testItem("b1", at = 20, source = b.id))
        val state = tileState(listOf(a, b)).first()
        assertEquals(listOf("a2", "b1", "a1"), state.rotation.map { it.key })
        assertEquals(3, state.unread)
    }

    @Test fun aTileWithNoSourcesHasAnEmptyState() = runTest {
        assertEquals(TileState(emptyList(), 0), tileState(emptyList()).first())
    }

    @Test fun theHiddenChatsOfEverySourceAreCollected() = runTest {
        a.hiddenState.value = listOf(HiddenChat(a.id, "family", "Family"))
        b.hiddenState.value = listOf(HiddenChat(b.id, "sam", "Sam"))
        assertEquals(listOf("Family", "Sam"), tileState(listOf(a, b)).first().hiddenChats.map { it.name })
    }
}
