package io.github.b1tsagar.liquiwin.feature.start

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileSource
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>() // Back: compose.activity.onBackPressedDispatcher
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val camera = testApp("Camera"); private val maps = testApp("Maps"); private val mail = testApp("Mail")
    private val store = FakeTileStore()
    private val catalog = FakeAppCatalog(apps = listOf(camera, maps, mail))
    private val cameraSource = FakeTileSource(SourceId.forApp(camera.key.packageName, 0))
    private val sources = SourceRegistry { if (it == cameraSource.id) cameraSource else null }

    // Pins the apps (medium tiles at (0,0), (2,0) and (4,0) by default), then shows the start screen.
    private fun show(pinned: List<LauncherApp> = listOf(camera, maps, mail), scroll: ScrollState? = null) {
        val viewModel = StartViewModel(store, catalog, gridWidth = 6, sources)
        pinned.forEach { viewModel.pin(it.key) }
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, scroll ?: rememberScrollState()) } }
    }

    private fun longPressCamera() = compose.onNodeWithTag("tile-1").performTouchInput { longClick() }

    // The tiles turnstile away first (0.4 s for three columns and then the tapped tile), and only then does the app open.
    @Test fun tappingATileFlipsTheTilesAwayThenOpensItsApp() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile-1").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(emptyList<AppKey>(), catalog.launched)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(camera.key), catalog.launched)
    }

    @Test fun longPressShowsTheEditControls() {
        show()
        longPressCamera()
        for (control in listOf("unpin", "resize", "done")) compose.onNodeWithContentDescription(control).assertExists()
    }

    @Test fun resizeMakesTheTileWide() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("resize").performClick()
        compose.waitForIdle()
        assertEquals(TileSize.WIDE, runBlocking { store.tiles.first() }.single { it.id == 1L }.size)
    }

    @Test fun unpinRemovesTheTile() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("unpin").performClick()
        compose.onNodeWithContentDescription("Camera").assertDoesNotExist()
    }

    @Test fun draggingSwapsTilesAfterTheDwell() {
        show()
        val resources = compose.activity.resources
        val stepPx = GridMetrics(resources.configuration.screenWidthDp.toFloat(), 6, 24f, 12f).stepDp * resources.displayMetrics.density
        compose.onNodeWithTag("tile-1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(2 * stepPx, 0f))
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        assertEquals(
            listOf(TilePlacement(2, 6, 0, 0), TilePlacement(1, 6, 2, 0), TilePlacement(3, 6, 4, 0)),
            runBlocking { store.placements(6).first() },
        )
    }

    // Spec §4.3 moves the lifted tile; a swipe anywhere else still scrolls the one vertical scroll (§4.2).
    @Test fun inEditModeASwipeOnAnotherTileScrollsInsteadOfMovingIt() {
        val apps = listOf(camera, maps, mail) + (1..15).map { testApp("App $it") }
        catalog.apps.value = apps
        val scroll = ScrollState(0)
        show(pinned = apps, scroll = scroll)
        longPressCamera()
        val before = runBlocking { store.placements(6).first() }
        compose.onNodeWithTag("tile-5").performTouchInput { swipe(center, center - Offset(0f, 140.dp.toPx())) }
        compose.waitForIdle()
        assertTrue(scroll.value > 0)
        assertEquals(before, runBlocking { store.placements(6).first() })
    }

    @Test fun backAndEmptySpaceLeaveEditMode() {
        show()
        longPressCamera()
        compose.onNodeWithContentDescription("done").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("done").assertDoesNotExist()

        longPressCamera()
        compose.onNodeWithContentDescription("done").assertExists()
        compose.onRoot().performTouchInput { click(Offset(centerX, height * 0.6f)) }
        compose.onNodeWithContentDescription("done").assertDoesNotExist()
    }

    @Test fun anEmptyStartScreenShowsTheHint() {
        show(pinned = emptyList())
        compose.onNodeWithText("swipe left for your apps. long-press one and choose pin to start.").assertExists()
    }

    @Test fun talkBackHearsTheUnreadCount() {
        cameraSource.itemState.value = List(3) { testItem("m$it", at = it.toLong(), source = cameraSource.id) }
        show()
        compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 unread"))
    }

    @Test fun tappingAContentFaceOpensTheItemAndTheLabelStripTheApp() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // the item shows at once
        val item = testItem("a", source = cameraSource.id)
        cameraSource.itemState.value = listOf(item)
        show()
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(centerX, 10f)) }
        compose.waitUntil(5_000) { cameraSource.opened.isNotEmpty() }
        assertEquals(listOf<TileItem?>(item), cameraSource.opened)
        assertEquals(emptyList<AppKey>(), catalog.launched)
        compose.mainClock.advanceTimeBy(2_000) // LiquiWin stayed in front, so the tiles come back
        compose.onNodeWithTag("tile-1").performTouchInput { click(Offset(centerX, height - 5f)) }
        compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
        assertEquals(listOf(camera.key), catalog.launched)
    }

    @OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
    @Test fun talkBackHearsTheItemAndCanOpenTheApp() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        cameraSource.itemState.value = listOf(testItem("a", source = cameraSource.id))
        show()
        compose.onNodeWithTag("tile-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 unread, Alex: see you at 6"))
        compose.onNodeWithTag("tile-1").performCustomAccessibilityActionWithLabel("open Camera")
        compose.waitUntil(5_000) { catalog.launched.isNotEmpty() }
        assertEquals(listOf(camera.key), catalog.launched)
    }
}
