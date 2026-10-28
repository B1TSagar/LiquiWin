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

    @Test fun theChatButtonIsTheBottomRightCornerOfAnItemFace() {
        fun at(x: Float, y: Float) =
            tileChatTarget(TileFace.Item(a), x, y, widthPx = 100f, heightPx = 100f, labelStripPx = 30f, buttonPx = 40f)
        assertEquals(a, at(60f, 60f)) // the corner reaches above the label strip
        assertEquals(a, at(99f, 99f))
        assertNull(at(59f, 99f))
        assertNull(at(99f, 59f))
        assertNull(tileChatTarget(TileFace.Front, 99f, 99f, 100f, 100f, 30f, 40f))
    }

    @Test fun eachStackRowHasItsOwnChatButton() {
        // A 90 px body of three 30 px rows, over a 40 px label strip
        fun at(x: Float, y: Float) = tileChatTarget(
            TileFace.Stack(listOf(a, b, c)), x, y, widthPx = 100f, heightPx = 130f, labelStripPx = 40f, buttonPx = 20f,
        )
        assertEquals(listOf(a, b, c), listOf(25f, 55f, 85f).map { at(90f, it) })
        assertNull(at(90f, 5f)) // above the first row's button
        assertNull(at(70f, 25f))
        assertNull(at(90f, 120f)) // the label strip opens the app
    }
}
