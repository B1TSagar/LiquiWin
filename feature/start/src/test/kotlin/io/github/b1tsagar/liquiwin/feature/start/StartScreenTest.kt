package io.github.b1tsagar.liquiwin.feature.start

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // Pins the apps (medium tiles at (0,0), (2,0) and (4,0) by default), then shows the start screen.
    private fun show(pinned: List<LauncherApp> = listOf(camera, maps, mail)) {
        val viewModel = StartViewModel(store, catalog, gridWidth = 6)
        pinned.forEach { viewModel.pin(it.key) }
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState()) } }
    }

    private fun longPressCamera() = compose.onNodeWithTag("tile-1").performTouchInput { longClick() }

    @Test fun tappingATileOpensItsApp() {
        show()
        compose.onNodeWithTag("tile-1").performClick()
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
}
