package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp

sealed interface SearchResult {
    val key: String

    data class App(val app: LauncherApp) : SearchResult {
        override val key: String get() = "app:" + app.key.flatten()
    }
}
