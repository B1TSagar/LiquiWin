package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Searches the launchable apps. */
class AppSearchProvider(private val catalog: AppCatalog) : SearchProvider {
    override val id = "apps"
    override val title = "apps"

    override fun search(query: String): Flow<List<SearchResult>> =
        combine(catalog.apps, catalog.lastLaunched) { apps, launched -> rankApps(query, apps, launched).map { SearchResult.App(it) } }
            .flowOn(Dispatchers.Default)
}
