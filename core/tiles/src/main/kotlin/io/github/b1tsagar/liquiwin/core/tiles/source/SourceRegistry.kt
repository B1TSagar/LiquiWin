package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId

/** Creates each source once, on first use. Gives null for an id nothing serves. Thread-safe. */
class SourceRegistry(private val create: (SourceId) -> TileSource?) {
    private val sources = HashMap<SourceId, TileSource?>()

    @Synchronized
    fun source(id: SourceId): TileSource? = if (id in sources) sources[id] else create(id).also { sources[id] = it }
}
