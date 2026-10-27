package io.github.b1tsagar.liquiwin.feature.search

import kotlinx.coroutines.flow.Flow

interface SearchProvider {
    val id: String
    val title: String
    fun search(query: String): Flow<List<SearchResult>>
}
