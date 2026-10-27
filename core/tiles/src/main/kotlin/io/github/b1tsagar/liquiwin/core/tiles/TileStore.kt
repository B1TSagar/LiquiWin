package io.github.b1tsagar.liquiwin.core.tiles

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import kotlinx.coroutines.flow.Flow

interface TileStore {
    /** Ordered by id. */
    val tiles: Flow<List<Tile>>

    /** In reading order: by y, then x. */
    fun placements(gridWidth: Int): Flow<List<TilePlacement>>

    suspend fun savedWidths(): Set<Int>

    suspend fun insertAppTile(app: AppKey, size: TileSize): Long

    suspend fun commit(edit: LayoutEdit)
}

data class LayoutEdit(
    val deleted: Set<Long> = emptySet(),
    val sizes: Map<Long, TileSize> = emptyMap(),
    val appKeys: Map<Long, AppKey> = emptyMap(),
    /** Replaces each listed width entirely. */
    val placements: Map<Int, List<TilePlacement>> = emptyMap(),
)
