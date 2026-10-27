package io.github.b1tsagar.liquiwin.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class SearchState(val query: String = "", val groups: List<SearchGroup> = emptyList())

data class SearchGroup(val providerId: String, val title: String, val results: List<SearchResult>)

/**
 * The query shows at once; results follow it 120 ms after typing stops. All providers search together, and their
 * groups keep provider order, leaving out the empty ones.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val providers: List<SearchProvider>, private val opener: SearchOpener) : ViewModel() {
    private val query = MutableStateFlow("")

    private val groups = query.debounce(120).flatMapLatest { q ->
        if (q.isBlank() || providers.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val searches = providers.map { provider ->
            provider.search(q).onStart { emit(emptyList()) }.map { SearchGroup(provider.id, provider.title, it) }
        }
        combine(searches) { all -> all.filter { it.results.isNotEmpty() } }
    }

    val state: StateFlow<SearchState> = combine(query, groups.onStart { emit(emptyList()) }) { q, g -> SearchState(q, g) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchState())

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    fun open(result: SearchResult) = opener.open(result)

    /** Opens the best result. False when there are no results. */
    fun openFirst(): Boolean {
        val first = state.value.groups.firstOrNull()?.results?.firstOrNull() ?: return false
        open(first)
        return true
    }
}
