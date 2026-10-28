package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.testing.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileTapTest {
    private val a = testItem("a"); private val b = testItem("b"); private val c = testItem("c")

    @Test fun theFrontFaceOpensTheApp() {
        assertNull(tileTapTarget(TileFace.Front, y = 10f, heightPx = 100f, labelStripPx = 30f))
    }

    @Test fun aContentFaceBodyOpensItsItemAndTheLabelStripTheApp() {
        assertEquals(a, tileTapTarget(TileFace.Item(a), y = 69f, heightPx = 100f, labelStripPx = 30f))
        assertNull(tileTapTarget(TileFace.Item(a), y = 71f, heightPx = 100f, labelStripPx = 30f))
    }

    @Test fun aStackSplitsItsBodyIntoThreeRows() {
        val stack = TileFace.Stack(listOf(a, b, c))
        assertEquals(listOf(a, b, c, null), listOf(10f, 35f, 60f, 80f).map { tileTapTarget(stack, it, 100f, 30f) })
        assertNull(tileTapTarget(TileFace.Stack(listOf(a)), 35f, 100f, 30f)) // no second row
    }
}
