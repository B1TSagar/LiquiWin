package io.github.b1tsagar.liquiwin.feature.applist

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val day = 86_400_000L
    private val now = 10 * day
    private val camera = testApp("Camera")

    @Test fun appsInstalledInTheLastThreeDaysAreNew() = runTest {
        val fresh = testApp("Fresh", firstInstallTime = now - 2 * day)
        val old = testApp("Old", firstInstallTime = now - 4 * day)
        val viewModel = AppListViewModel(FakeAppCatalog(apps = listOf(fresh, old)), flowOf(emptySet()), {}, nowMillis = { now })
        assertEquals(setOf(fresh.key), viewModel.state.first { it.index.sections.isNotEmpty() }.newApps)
    }

    @Test fun pinCallsThePinCallback() {
        val pinned = mutableListOf<AppKey>()
        AppListViewModel(FakeAppCatalog(apps = listOf(camera)), flowOf(emptySet()), { pinned += it }).pin(camera)
        assertEquals(listOf(camera.key), pinned)
    }

    @Test fun openingAPausedWorkAppRequestsUnpause() {
        val slack = testApp("Slack", userSerial = 10, isPaused = true)
        val catalog = FakeAppCatalog(apps = listOf(slack))
        AppListViewModel(catalog, flowOf(emptySet()), {}).open(slack, null, null)
        assertEquals(listOf(10L), catalog.unpauseRequests)
        assertEquals(emptyList<AppKey>(), catalog.launched)
    }
}
