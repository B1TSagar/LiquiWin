package io.github.b1tsagar.liquiwin.feature.start

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

    // Pins medium tiles, three to a row, for the first [pinned] apps, then shows start with the clock held.
    private fun show(pinned: Int = 1) {
        viewModel = StartViewModel(FakeTileStore(), FakeAppCatalog(apps = apps), gridWidth = 6, SourceRegistry(sources::get))
        apps.take(pinned).forEach { viewModel.pin(it.key) }
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState(), liveTilesPaused = paused) } }
    }

    private fun said(tile: Int): String? =
        compose.onNodeWithTag("tile-$tile").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

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
}
