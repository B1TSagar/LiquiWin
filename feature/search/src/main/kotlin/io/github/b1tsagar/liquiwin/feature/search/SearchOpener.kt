package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog

class SearchOpener(private val catalog: AppCatalog) {
    /** A paused work app asks to unpause its profile instead of launching. */
    fun open(result: SearchResult) = when (result) {
        is SearchResult.App ->
            if (result.app.isPaused) catalog.requestUnpause(result.app.key.userSerial) else catalog.launch(result.app.key)
    }
}
