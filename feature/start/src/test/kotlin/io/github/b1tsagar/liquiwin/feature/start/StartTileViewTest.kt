package io.github.b1tsagar.liquiwin.feature.start

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.testing.testItem
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.feature.start.layout.Cell
import io.github.b1tsagar.liquiwin.feature.start.live.TileFace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartTileViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val metrics = GridMetrics(411f, 6, 24f, 12f)
    private fun tile(size: TileSize) = StartTile(1, size, Cell(0, 0), testApp("Chat"), "Chat", isWork = false, isPaused = false)

    @Test fun theFrontFaceShowsTheCountAndCapsItAt99Plus() {
        var unread by mutableIntStateOf(5)
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.SMALL), metrics, { null }, unread = unread) } }
        compose.onNodeWithText("5").assertExists()
        unread = 120
        compose.onNodeWithText("99+").assertExists()
    }

    // Review Focus 4
    @Test fun noCountShowsAtZeroOrWhilePaused() {
        var unread by mutableIntStateOf(0)
        var paused by mutableStateOf(false)
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, unread = unread, liveTilesPaused = paused) } }
        compose.onNodeWithText("0").assertDoesNotExist()
        unread = 3
        paused = true
        compose.onNodeWithText("3").assertDoesNotExist()
        compose.onNodeWithTag("live-paused").assertExists()
    }

    @Test fun aMediumContentFaceShowsSenderAndText() {
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.MEDIUM), metrics, { null }, face = TileFace.Item(testItem("a"))) } }
        for (text in listOf("Alex", "see you at 6", "Chat")) compose.onNodeWithText(text).assertExists()
    }

    @Test fun aLargeTileStacksThreeItems() {
        val stack = TileFace.Stack(listOf(testItem("a", title = "Alex"), testItem("b", title = "Sam"), testItem("c", title = "Jo")))
        compose.setContent { LiquiWinTheme { StartTileView(tile(TileSize.LARGE), metrics, { null }, face = stack) } }
        for (name in listOf("Alex", "Sam", "Jo")) compose.onNodeWithText(name).assertExists()
    }
}
