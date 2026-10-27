package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())
    private val camera = SearchResult.App(testApp("Camera"))
    private val opener = SearchOpener(FakeAppCatalog())

    private class CountingProvider(override val id: String, private val results: () -> Flow<List<SearchResult>>) : SearchProvider {
        override val title = id
        val queries = mutableListOf<String>()

        override fun search(query: String): Flow<List<SearchResult>> {
            queries += query
            return results()
        }
    }

    @Test fun waitsFor120msBeforeSearching() = runTest {
        val provider = CountingProvider("a") { flowOf(listOf(camera)) }
        val viewModel = SearchViewModel(listOf(provider), opener)
        viewModel.onQueryChange("ca")
        advanceTimeBy(119)
        assertEquals(0, provider.queries.size)
        advanceTimeBy(2)
        assertEquals(1, provider.queries.size)
    }

    @Test fun groupsFollowProviderOrderAndSkipEmptyOnes() = runTest {
        val late = CountingProvider("a") { flow { delay(500); emit(listOf(camera)) } }
        val empty = CountingProvider("b") { flowOf(emptyList()) }
        val found = CountingProvider("c") { flowOf(listOf(camera)) }
        val viewModel = SearchViewModel(listOf(late, empty, found), opener)
        viewModel.onQueryChange("ca")
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), viewModel.state.value.groups.map { it.providerId })
    }

    @Test fun aNewQueryReplacesTheOldOne() = runTest {
        val provider = CountingProvider("a") { flowOf(listOf(camera)) }
        val viewModel = SearchViewModel(listOf(provider), opener)
        viewModel.onQueryChange("c")
        advanceTimeBy(50)
        viewModel.onQueryChange("ca")
        advanceUntilIdle()
        assertEquals(listOf("ca"), provider.queries)
    }
}
