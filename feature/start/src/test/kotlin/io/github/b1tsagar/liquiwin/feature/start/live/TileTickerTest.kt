package io.github.b1tsagar.liquiwin.feature.start.live

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileTickerTest {
    @Test fun gapsAreWholeTenthsFromATenthToThreeSeconds() {
        val ticker = TileTicker(Random(1))
        val gaps = List(1_000) { ticker.nextGapMs() }
        assertTrue(gaps.all { it in 100L..3_000L && it % 100 == 0L })
        assertEquals(100L, gaps.min())
        assertEquals(3_000L, gaps.max())
    }

    @Test fun aTileThatMovedSitsOutThreeTicks() {
        val ticker = TileTicker(Random(1))
        assertEquals(listOf(7L, null, null, null, 7L), List(5) { ticker.tick(listOf(7L)) })
    }

    @Test fun eachTickMovesOneTileAndRestingOnesAreSkipped() {
        val ticker = TileTicker(Random(1))
        val moved = List(40) { ticker.tick(listOf(1L, 2L, 3L, 4L, 5L))!! }
        moved.forEachIndexed { i, id -> assertFalse(id in moved.subList(maxOf(0, i - 3), i)) }
    }

    @Test fun aTileThatJumpedRestsToo() {
        val ticker = TileTicker(Random(1))
        ticker.jumped(7, nowMs = 0)
        assertEquals(listOf(null, null, null, 7L), List(4) { ticker.tick(listOf(7L)) })
    }

    @Test fun jumpsAreAtMostOncePer600Ms() {
        val ticker = TileTicker(Random(1))
        assertEquals(0L, ticker.jumpDelayMs(7, nowMs = 1_000))
        ticker.jumped(7, nowMs = 1_000)
        assertEquals(400L, ticker.jumpDelayMs(7, nowMs = 1_200))
        assertEquals(0L, ticker.jumpDelayMs(7, nowMs = 1_600))
        assertEquals(0L, ticker.jumpDelayMs(8, nowMs = 1_200))
    }
}
