package io.github.b1tsagar.liquiwin.feature.start

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.motion.TILE_FLIP_MS
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import org.junit.Assert.assertEquals
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

    // Pins medium tiles, three to a row, for the first [pinned] apps, then shows start with the clock held. [large] makes
    // the first one large.
    private fun show(pinned: Int = 1, large: Boolean = false) {
        viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = apps), gridWidth = 6, SourceRegistry(sources::get))
        apps.take(pinned).forEach { viewModel.pin(it.key) }
        if (large) repeat(2) { viewModel.cycleSize(1) } // medium, wide, large
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState(), liveTilesPaused = paused) } }
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
    }

    // The owner's call: a large tile lists every unread message. TalkBack hears the rows in view and pages through the rest.
    @Test fun talkBackHearsALargeTilesRowsInViewAndPagesThroughTheRest() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the list shows at once
        showLarge(messages = 7)
        assertEquals("7 unread, P6: t6, P5: t5, P4: t4", said(1))
        assertEquals(listOf("open App 1", "open P5", "open P4", "chat with P6", "chat with P5", "chat with P4", "older messages"), actions(1))
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
}
