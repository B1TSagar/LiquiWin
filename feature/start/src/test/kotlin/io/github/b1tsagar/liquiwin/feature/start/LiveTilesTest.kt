package io.github.b1tsagar.liquiwin.feature.start

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.motion.TILE_FLIP_MS
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testCall
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTilesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val apps = (1..21).map { testApp("App $it") }
    private val sources = apps.associate { app -> SourceId.forApp(app.key.packageName, 0).let { it to FakeTileSource(it) } }
    private fun sourceOf(tile: Int) = sources.getValue(SourceId.forApp(apps[tile - 1].key.packageName, 0))
    private val chat get() = sourceOf(1)
    private var paused by mutableStateOf(false)
    private lateinit var viewModel: StartViewModel
    private val scroll = ScrollState(0)
    private val chats = mutableListOf<TileItem>()

    // Pins medium tiles, three to a row, for the first [pinned] apps, then shows start with the clock held. [large] makes
    // the first one large.
    private fun show(pinned: Int = 1, large: Boolean = false) {
        viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = apps), gridWidth = 6, SourceRegistry(sources::get))
        apps.take(pinned).forEach { viewModel.pin(it.key) }
        if (large) repeat(2) { viewModel.cycleSize(1) } // medium, wide, large
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, scroll, liveTilesPaused = paused, onChat = { chats += it }) } }
    }

    // Tile 1 large, with [messages] unread messages from P(n-1), the newest, down to P0.
    private fun showLarge(messages: Int) {
        chat.itemState.value = (messages - 1 downTo 0).map { testItem("m$it", title = "P$it", text = "t$it", at = it.toLong(), source = chat.id) }
        show(large = true)
        compose.mainClock.advanceTimeByFrame() // the list's first layout tells the tile whether it scrolls
    }

    private fun said(tile: Int): String? =
        compose.onNodeWithTag("tile-$tile").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    private fun actions(tile: Int): List<String> =
        compose.onNodeWithTag("tile-$tile").fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    private fun page(tile: Int, action: String) {
        compose.onNodeWithTag("tile-$tile").performCustomAccessibilityActionWithLabel(action)
        compose.mainClock.advanceTimeBy(1_000)
    }

    // What the tile says every 100 ms for [ms].
    private fun saidOver(tile: Int, ms: Long) = (1..ms / 100).map { compose.mainClock.advanceTimeBy(100); said(tile) }

    // A swipe across [x] of the tile's width and [y] of its height from its centre. 300 ms passes touch slop (16 px under
    // Robolectric) well inside the 400 ms long-press timeout, even on a short swipe, and is no flick. A flick needs two
    // moves or more, as Compose's velocity tracker needs three points: 30 ms makes two.
    private fun swipe(tile: Int, x: Float = 0f, y: Float = 0f, ms: Long = 300) {
        compose.onNodeWithTag("tile-$tile").performTouchInput { swipe(center, center + Offset(x * width, y * height), ms) }
        compose.mainClock.advanceTimeBy(100)
    }

    private fun motionOff() = Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

    @Test fun theFirstTickFlipsATileToItsNewestItemAfter2_5Seconds() {
        chat.itemState.value = listOf(testItem("a"))
        show()
        compose.mainClock.advanceTimeBy(2_400)
        assertEquals("1 unread", said(1))
        compose.mainClock.advanceTimeBy(100L + TILE_FLIP_MS)
        assertEquals("1 unread, Alex: see you at 6", said(1))
    }

    @Test fun aNewItemFlipsItsTileWithinASecond() {
        show()
        compose.mainClock.advanceTimeBy(500)
        chat.itemState.value = listOf(testItem("a", text = "running late"))
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun onlyTilesOnScreenTakePart() {
        sourceOf(21).itemState.value = listOf(testItem("a", text = "below the fold"))
        show(pinned = 21) // seven rows; the last is below Robolectric's 470 dp screen
        assertTrue(saidOver(21, 20_000).none { it.orEmpty().contains("below the fold") })
        // performScrollTo repeats its animated scroll until the tile is in view, so the clock must run meanwhile.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("tile-21").performScrollTo()
        compose.mainClock.autoAdvance = false
        assertTrue(saidOver(21, 20_000).any { it.orEmpty().contains("below the fold") })
    }

    @Test fun withAnimationsOffTilesShowTheirNewestItemWithoutATimer() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        chat.itemState.value = listOf(testItem("b", text = "newest", at = 20), testItem("a", text = "older", at = 10))
        show()
        assertEquals(setOf("2 unread, Alex: newest"), saidOver(1, 20_000).toSet())
    }

    @Test fun tilesStayStillInEditMode() {
        chat.itemState.value = listOf(testItem("a"))
        show()
        viewModel.enterEdit(1)
        assertEquals(setOf("1 unread"), saidOver(1, 20_000).toSet())
    }

    // Review Focus 4
    @Test fun pausedTilesShowOnlyTheirFront() {
        paused = true
        chat.itemState.value = listOf(testItem("a"))
        show()
        assertEquals(setOf("live tile paused"), saidOver(1, 20_000).toSet())
        assertEquals(emptyList<String>(), actions(1)) // a paused tile takes no swipes (Review Focus 1)
    }

    // The owner's call: a large tile lists every unread message. TalkBack hears the rows in view and pages through the rest.
    @Test fun talkBackHearsALargeTilesRowsInViewAndPagesThroughTheRest() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the list shows at once
        showLarge(messages = 7)
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1))
        assertEquals(
            listOf(
                "open App 1", "open P5", "open P4", "chat with P6", "chat with P5", "chat with P4", "clear P6", "clear P5", "clear P4",
                "older messages", "show front",
            ),
            actions(1),
        )
        page(1, "older messages")
        assertEquals("7 unread, P3: t3, P2: t2, P1: t1", said(1))
        page(1, "older messages") // as far as the list goes
        assertEquals("7 unread, P2: t2, P1: t1, P0: t0", said(1))
        assertEquals(listOf("newer messages"), actions(1).filter { it.endsWith(" messages") })
        page(1, "newer messages")
        page(1, "newer messages")
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1))
    }

    @Test fun aLargeTileStopsFlippingWhileItsListIsScrolled() {
        showLarge(messages = 7)
        compose.mainClock.advanceTimeBy(2_500L + TILE_FLIP_MS) // the first tick turns it to its list
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1))
        page(1, "older messages")
        assertEquals(setOf("7 unread, P3: t3, P2: t2, P1: t1"), saidOver(1, 20_000).toSet())
        page(1, "newer messages")
        assertTrue(saidOver(1, 20_000).contains("7 unread")) // back at the top, it turns to its front again
    }

    @Test fun aNewMessageBringsALargeTilesListBackToTheTop() {
        showLarge(messages = 7)
        compose.mainClock.advanceTimeBy(2_500L + TILE_FLIP_MS)
        page(1, "older messages")
        chat.itemState.value = listOf(testItem("m7", title = "P7", text = "t7", at = 7, source = chat.id)) + chat.itemState.value
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("8 unread, P7: t7, P6: t6, P5: t5", said(1))
    }

    // §5.3: a page stays until it's swiped back, where a front with a rotation turns on the timer
    @Test fun aSwipeDownTurnsATileToItsNewMessagesUntilTheNextOne() {
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertTrue(saidOver(1, 30_000).all { it == "1 unread, Alex: running late" })
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
    }

    // §5.4: a page of two or more items turns on the timer, never to the front
    @Test fun aPageOfTwoTurnsBetweenItsItemsButNeverToTheFront() {
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id), testItem("a", at = 10, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertEquals(setOf("2 unread, Alex: running late", "2 unread, Alex: see you at 6"), saidOver(1, 30_000).toSet())
    }

    @Test fun withNothingNewASwipeDownShowsTheHistory() {
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        assertEquals(listOf("show history"), actions(1))
        swipe(1, y = 0.5f)
        assertEquals("history, Alex: see you", said(1))
        assertEquals(listOf("open App 1", "chat with Alex", "show front"), actions(1))
    }

    // Review Focus 1
    @Test fun withStartScrolledDownASwipeDownScrollsStartInstead() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show(pinned = 21)
        swipe(4, y = -0.3f)
        assertTrue(scroll.value > 0)
        swipe(1, y = 0.5f)
        assertEquals(0, scroll.value)
        assertEquals("1 unread, Alex: see you at 6", said(1)) // still its newest, not turned to its front
    }

    // Review Focus 1
    @Test fun aTileWithNothingForEitherPageHasNone() {
        show()
        assertEquals(emptyList<String>(), actions(1))
        swipe(1, y = 0.5f)
        assertNull(said(1))
    }

    @Test fun aSwipeDownScrollsALargeTilesListBackBeforeItTurnsTheTile() {
        motionOff()
        showLarge(messages = 7)
        page(1, "older messages")
        swipe(1, y = 1f)
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1)) // back at its top, and still the list
        swipe(1, y = 0.5f)
        assertEquals("7 unread", said(1))
    }

    @Test fun withMotionOffASwipeDownStepsThroughNewestFrontAndPage() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id))
        show()
        assertEquals("1 unread, Alex: running late", said(1))
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
        swipe(1, y = 0.25f, ms = 30) // a flick
        assertEquals("1 unread, Alex: running late", said(1))
        swipe(1, y = 0.2f) // short and slow
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun withMotionOffANewItemShowsEvenAfterASwipeToTheFront() {
        motionOff()
        chat.itemState.value = listOf(testItem("a", at = 10, source = chat.id))
        show()
        swipe(1, y = 0.5f)
        assertEquals("1 unread", said(1))
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id)) + chat.itemState.value
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("2 unread, Alex: running late", said(1))
    }

    @Test fun talkBackCanTurnATileToItsPagesAndBack() {
        motionOff()
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        page(1, "show history")
        assertEquals("history, Alex: see you", said(1))
        page(1, "show front")
        assertNull(said(1))
        assertEquals(listOf("show history"), actions(1))
    }

    // Review Focus 4
    @Test fun aNewMessagesPageWhoseItemsAreAllReadGivesWayToTheFront() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show()
        page(1, "show front")
        page(1, "show new messages")
        chat.itemState.value = listOf(testItem("b", at = 20, unread = false, source = chat.id))
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(said(1))
        assertEquals(listOf("show history"), actions(1))
    }

    // Review Focus 5: §5.4's touched tile
    @Test fun aTileSitsOutTheTicksWhileAFingerIsOnIt() {
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id), testItem("a", at = 10, source = chat.id))
        show()
        compose.onNodeWithTag("tile-1").performTouchInput { down(center); moveBy(Offset(0f, height / 4f)) } // past slop
        assertTrue(saidOver(1, 20_000).all { it == "2 unread" })
        compose.onNodeWithTag("tile-1").performTouchInput { moveBy(Offset(0f, -height / 4f)); up() }
        assertTrue(saidOver(1, 20_000).any { it != "2 unread" })
    }

    // Review Focus 4 and 5: a new item turns history to it, once the finger lifts
    @Test fun aNewItemTurnsAHistoryPageToItOnceTheFingerLifts() {
        chat.itemState.value = listOf(testItem("a", text = "see you", at = 10, unread = false, source = chat.id))
        show()
        page(1, "show history")
        compose.onNodeWithTag("tile-1").performTouchInput { down(center); moveBy(Offset(0f, height / 4f)) } // past slop
        chat.itemState.value = listOf(testItem("b", text = "running late", at = 20, source = chat.id)) + chat.itemState.value
        assertTrue(saidOver(1, 3_000).all { it == "history, 1 unread, Alex: see you" })
        compose.onNodeWithTag("tile-1").performTouchInput { moveBy(Offset(0f, -height / 4f)); up() }
        // Android applies the lift's state at once. The test clock applies it only when a coroutine next resumes, as the
        // timer's does at random gaps, so about 1 run in 10 had none within 2 s and stayed on history.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("1 unread, Alex: running late", said(1))
    }

    @Test fun aSwipeLeftOnAMessageOpensItsChat() {
        motionOff()
        val b = testItem("b", text = "running late", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = -0.5f)
        assertEquals(listOf(b), chats)
        assertEquals("1 unread, Alex: running late", said(1))
    }

    // The owner's call after the tile gestures were built: a missed call's swipe left presses its first button, call back
    @Test fun aSwipeLeftOnAMissedCallPressesItsFirstButton() {
        motionOff()
        val call = testCall(source = chat.id)
        chat.itemState.value = listOf(call)
        show()
        swipe(1, x = -0.5f)
        compose.mainClock.advanceTimeBy(2_000) // the tiles turn away before the press
        assertEquals(listOf(call to 0), chat.pressed)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun aSwipeRightOnAMessageClearsIt() {
        motionOff()
        val b = testItem("b", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = 0.5f)
        assertEquals(listOf(b), chat.cleared)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun aShortSlowSwipeSlidesTheMessageBackAndAFlickActs() {
        motionOff()
        val b = testItem("b", at = 20, source = chat.id)
        chat.itemState.value = listOf(b)
        show()
        swipe(1, x = -0.25f)
        swipe(1, x = 0.25f)
        assertEquals(emptyList<TileItem>(), chats)
        assertEquals(emptyList<TileItem>(), chat.cleared)
        swipe(1, x = -0.25f, ms = 30) // a flick
        assertEquals(listOf(b), chats)
    }

    // Review Focus 1
    @Test fun aMostlyUpwardSwipeOnAMessageScrollsStart() {
        motionOff()
        chat.itemState.value = listOf(testItem("b", at = 20, source = chat.id))
        show(pinned = 21)
        swipe(1, x = -0.2f, y = -0.5f)
        assertTrue(scroll.value > 0)
        assertEquals(emptyList<TileItem>(), chats)
    }

    @Test fun onAHistoryPageAMessageCanOnlyOpenItsChat() {
        motionOff()
        val a = testItem("a", text = "see you", at = 10, unread = false, source = chat.id)
        chat.itemState.value = listOf(a)
        show()
        page(1, "show history")
        assertFalse(actions(1).any { it.startsWith("clear") })
        swipe(1, x = 0.5f)
        assertEquals(emptyList<TileItem>(), chat.cleared)
        swipe(1, x = -0.5f)
        assertEquals(listOf(a), chats)
    }

    @Test fun aSwipeOnALargeTilesRowActsOnThatRow() {
        motionOff()
        showLarge(messages = 7) // P6, P5 and P4 in view, and 0.45 of the tile down is the middle row
        compose.onNodeWithTag("tile-1").performTouchInput {
            swipe(Offset(centerX, height * 0.45f), Offset(centerX - width / 2, height * 0.45f), 300)
        }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf("m5"), chats.map { it.key })
    }

    @Test fun talkBackCanClearEachMessageInView() {
        motionOff()
        showLarge(messages = 7)
        page(1, "clear P5")
        assertEquals(listOf("m5"), chat.cleared.map { it.key })
    }

    // Final review: a swipe on another message takes over from one still clearing, which clears all the same. The fake
    // source keeps a cleared message, so P5's let-go runs its whole course: out, a second's wait, and back.
    @Test fun aSwipeOnAnotherMessageTakesOverFromOneStillClearing() {
        showLarge(messages = 7) // P6, P5 and P4 in view, and 0.45 of the tile down is the middle row
        page(1, "show new messages")
        compose.onNodeWithTag("tile-1").performTouchInput {
            swipe(Offset(centerX, height * 0.45f), Offset(centerX + width / 2, height * 0.45f), 300) // P5 slides out
            down(Offset(centerX, height * 0.2f)) // P6, held a quarter of the tile to the left while P5 is still sliding
            moveBy(Offset(-width / 8f, 0f), 50)
            moveBy(Offset(-width / 8f, 0f), 50)
        }
        compose.mainClock.advanceTimeBy(2_000) // past the whole of P5's let-go
        compose.onNodeWithText("reply", useUnmergedTree = true).assertExists()
        assertEquals(listOf("m5"), chat.cleared.map { it.key })
        compose.onNodeWithTag("tile-1").performTouchInput { up() }
    }
}
