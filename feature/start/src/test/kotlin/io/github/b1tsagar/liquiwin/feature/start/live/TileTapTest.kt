package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.testing.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileTapTest {
    private val a = testItem("a"); private val b = testItem("b"); private val c = testItem("c"); private val d = testItem("d")

    // A list from y 10 to 100 of 30 px rows, scrolled 15 px: the first row shows its lower half, the fourth its upper.
    private val scrolled = StackLayout(
        rows = listOf(StackRow(0, -5f, 25f), StackRow(1, 25f, 55f), StackRow(2, 55f, 85f), StackRow(3, 85f, 115f)),
        top = 10f,
        bottom = 100f,
    )

    @Test fun theFrontFaceOpensTheApp() {
        assertNull(tileTapTarget(TileFace.Front, y = 10f, heightPx = 100f, labelStripPx = 30f))
    }

    @Test fun aContentFaceBodyOpensItsItemAndTheLabelStripTheApp() {
        assertEquals(a, tileTapTarget(TileFace.Item(a), y = 69f, heightPx = 100f, labelStripPx = 30f))
        assertNull(tileTapTarget(TileFace.Item(a), y = 71f, heightPx = 100f, labelStripPx = 30f))
    }

    // The Galaxy check found a large tile's rows hit-tested from its top edge, not its inset, so a tap on the lower part
    // of a row's chat button opened the row below. The rows come from the list as laid out now.
    @Test fun aStackOpensTheRowUnderTheFingerWhereverItsListHasScrolled() {
        val stack = TileFace.Stack(listOf(a, b, c, d))
        // The margin above the list opens its top row, and the label strip below it the app.
        assertEquals(listOf(a, a, b, c, d, null), listOf(5f, 20f, 30f, 60f, 95f, 100f).map { tileTapTarget(stack, it, 130f, 30f, scrolled) })
        assertNull(tileTapTarget(TileFace.Stack(listOf(a)), 30f, 130f, 30f, scrolled)) // no item for that row
    }

    @Test fun theChatButtonIsTheBottomRightCornerOfAnItemFace() {
        fun at(x: Float, y: Float) = tileChatTarget(TileFace.Item(a), x, y, widthPx = 100f, heightPx = 100f, buttonPx = 40f)
        assertEquals(a, at(60f, 60f)) // the corner reaches above the label strip
        assertEquals(a, at(99f, 99f))
        assertNull(at(59f, 99f))
        assertNull(at(99f, 59f))
        assertNull(tileChatTarget(TileFace.Front, 99f, 99f, 100f, 100f, 40f))
    }

    @Test fun eachStackRowHasItsChatButtonAtItsBottomEnd() {
        fun at(x: Float, y: Float) =
            tileChatTarget(TileFace.Stack(listOf(a, b, c, d)), x, y, widthPx = 100f, heightPx = 130f, buttonPx = 20f, stack = scrolled)
        assertEquals(listOf(a, b, c, d), listOf(15f, 50f, 70f, 99f).map { at(90f, it) }) // a button part-way out works where it shows
        assertNull(at(90f, 30f)) // the second row, above its button
        assertNull(at(70f, 50f)) // left of the buttons
        assertNull(at(90f, 5f)) // above the list, where the first row's button has scrolled out of sight
        assertNull(at(90f, 100f)) // the label strip opens the app
    }
}
