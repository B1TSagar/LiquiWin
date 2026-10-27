package io.github.b1tsagar.liquiwin.feature.applist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.testApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val camera = testApp("Camera"); private val maps = testApp("Maps")
    private val pinned = mutableListOf<AppKey>()

    // Grouping runs off the main thread, so wait for the first app to show.
    private fun show(apps: List<LauncherApp> = listOf(camera, maps), pinnedApps: Flow<Set<AppKey>> = flowOf(emptySet())) {
        val viewModel = AppListViewModel(FakeAppCatalog(apps = apps), pinnedApps, { pinned += it })
        compose.setContent { LiquiWinTheme { AppListScreen(viewModel, { null }, onSearch = {}, resetKey = 0) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(apps.first().label).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun longPressThenPinToStartCallsPin() {
        show()
        compose.onNodeWithText("Camera").performTouchInput { longClick() }
        compose.onNodeWithText("pin to start").performClick()
        assertEquals(listOf(camera.key), pinned)
    }

    @Test fun pinToStartIsHiddenForPinnedApps() {
        show(pinnedApps = flowOf(setOf(camera.key)))
        compose.onNodeWithText("Camera").performTouchInput { longClick() }
        compose.onNodeWithText("app info").assertExists()
        compose.onNodeWithText("pin to start").assertDoesNotExist()
    }

    @Test fun tappingAHeaderOpensTheGridAndJumps() {
        val apps = (0 until 40).map { testApp("${'A' + it % 26}app$it") }
        show(apps)
        compose.onNodeWithTag("header-c").performClick()
        compose.onNodeWithTag("letter-w").performClick()
        compose.onNodeWithText("Wapp22").assertIsDisplayed()
    }
}
