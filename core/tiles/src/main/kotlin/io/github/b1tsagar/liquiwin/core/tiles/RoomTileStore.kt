package io.github.b1tsagar.liquiwin.core.tiles

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.db.layout.TileDao
import io.github.b1tsagar.liquiwin.core.data.db.layout.TileEntity
import io.github.b1tsagar.liquiwin.core.data.db.layout.TilePlacementEntity
import io.github.b1tsagar.liquiwin.core.data.db.layout.TileWithSources
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind
import io.github.b1tsagar.liquiwin.core.tiles.model.TilePlacement
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.model.TileTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTileStore(private val dao: TileDao) : TileStore {
    override val tiles: Flow<List<Tile>> = dao.observeTiles().map { rows -> rows.map { it.toTile() } }

    override fun placements(gridWidth: Int): Flow<List<TilePlacement>> =
        dao.observePlacements(gridWidth).map { rows -> rows.map { TilePlacement(it.tileId, it.gridWidth, it.x, it.y) } }

    override suspend fun savedWidths(): Set<Int> = dao.savedWidths().toSet()

    override suspend fun insertAppTile(app: AppKey, size: TileSize): Long = dao.insertTileWithSources(
        TileEntity(kind = TileKind.APP.name, size = size.name, template = TileTemplate.FLIP.name, appKey = app.flatten()),
        listOf(SourceId.forApp(app.packageName, app.userSerial).value),
    )

    override suspend fun commit(edit: LayoutEdit) = dao.commit(
        deleted = edit.deleted,
        sizes = edit.sizes.mapValues { it.value.name },
        appKeys = edit.appKeys.mapValues { it.value.flatten() },
        placements = edit.placements.mapValues { (gridWidth, rows) -> rows.map { TilePlacementEntity(it.tileId, gridWidth, it.x, it.y) } },
    )
}

private fun TileWithSources.toTile() = Tile(
    id = tile.id,
    kind = TileKind.valueOf(tile.kind),
    title = tile.title,
    size = TileSize.valueOf(tile.size),
    template = TileTemplate.valueOf(tile.template),
    colorOverride = tile.colorOverride,
    appKey = tile.appKey?.let(AppKey::parse),
    sources = sources.sortedBy { it.sortOrder }.map { SourceId(it.sourceId) },
)
