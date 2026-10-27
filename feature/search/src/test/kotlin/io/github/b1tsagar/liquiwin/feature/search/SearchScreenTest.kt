package io.github.b1tsagar.liquiwin.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule() // the debounce runs on the test scheduler

    @Test fun goOpensTheFirstResult() {
        val camera = testApp("Camera")
        val catalog = FakeAppCatalog(apps = listOf(camera, testApp("Maps")))
        val viewModel = SearchViewModel(listOf(AppSearchProvider(catalog)), SearchOpener(catalog))
        var opened = false
        compose.setContent { LiquiWinTheme { SearchScreen(viewModel, { null }, onOpened = { opened = true }) } }

        compose.onNode(hasSetTextAction()).performTextInput("cam")
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(200)
        compose.waitUntil(5_000) { viewModel.state.value.groups.isNotEmpty() }
        compose.onNode(hasSetTextAction()).performImeAction()

        assertEquals(listOf(camera.key), catalog.launched)
        assertTrue(opened)
    }
}
