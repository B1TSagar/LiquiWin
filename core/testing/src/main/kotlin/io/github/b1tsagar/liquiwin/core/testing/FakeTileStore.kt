package io.github.b1tsagar.liquiwin.core.testing

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.tiles.LayoutEdit
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.model.TileTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [TileStore] that behaves like the Room one, deletes cascading to placements included. */
class FakeTileStore : TileStore {
    private val tileState = MutableStateFlow<List<Tile>>(emptyList())
    private val placementState = MutableStateFlow<Map<Int, List<TilePlacement>>>(emptyMap())
    private var nextId = 1L
    var commitCount: Int = 0

    override val tiles: Flow<List<Tile>> = tileState

    override fun placements(gridWidth: Int): Flow<List<TilePlacement>> =
        placementState.map { all -> all[gridWidth].orEmpty().sortedWith(compareBy({ it.y }, { it.x })) }

    override suspend fun savedWidths(): Set<Int> = placementState.value.filterValues { it.isNotEmpty() }.keys

    override suspend fun insertAppTile(app: AppKey, size: TileSize): Long {
        val id = nextId++
        val tile = Tile(id, TileKind.APP, null, size, TileTemplate.FLIP, null, app, listOf(SourceId.forApp(app.packageName, app.userSerial)))
        tileState.value = (tileState.value + tile).sortedBy { it.id }
        return id
    }

    override suspend fun commit(edit: LayoutEdit) {
        commitCount++
        tileState.value = tileState.value.filter { it.id !in edit.deleted }
            .map { it.copy(size = edit.sizes[it.id] ?: it.size, appKey = edit.appKeys[it.id] ?: it.appKey) }
        placementState.value = (placementState.value + edit.placements)
            .mapValues { (gridWidth, rows) -> rows.filter { it.tileId !in edit.deleted }.map { it.copy(gridWidth = gridWidth) } }
    }

    fun seed(tiles: List<Tile>, placements: Map<Int, List<TilePlacement>>) {
        tileState.value = tiles.sortedBy { it.id }
        placementState.value = placements
        nextId = (tiles.maxOfOrNull { it.id } ?: 0) + 1
    }
}
