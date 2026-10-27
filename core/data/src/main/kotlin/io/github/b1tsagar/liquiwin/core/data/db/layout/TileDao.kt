package io.github.b1tsagar.liquiwin.core.data.db.layout

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TileWithSources(
    @Embedded val tile: TileEntity,
    @Relation(parentColumn = "id", entityColumn = "tile_id") val sources: List<TileSourceEntity>,
)

@Dao
abstract class TileDao {
    @Transaction
    @Query("SELECT * FROM tile ORDER BY id")
    abstract fun observeTiles(): Flow<List<TileWithSources>>

    @Query("SELECT * FROM tile_placement WHERE grid_width = :gridWidth ORDER BY y, x")
    abstract fun observePlacements(gridWidth: Int): Flow<List<TilePlacementEntity>>

    @Query("SELECT DISTINCT grid_width FROM tile_placement")
    abstract suspend fun savedWidths(): List<Int>

    @Query("SELECT COUNT(*) FROM tile_source")
    abstract suspend fun countSources(): Int

    @Transaction
    open suspend fun insertTileWithSources(tile: TileEntity, sourceIds: List<String>): Long {
        val id = insertTile(tile)
        insertSources(sourceIds.mapIndexed { order, sourceId -> TileSourceEntity(id, sourceId, order) })
        return id
    }

    /** Deletes, then updates sizes, then app keys, then replaces each listed width's rows, skipping deleted ids. */
    @Transaction
    open suspend fun commit(
        deleted: Set<Long>,
        sizes: Map<Long, String>,
        appKeys: Map<Long, String>,
        placements: Map<Int, List<TilePlacementEntity>>,
    ) {
        if (deleted.isNotEmpty()) deleteTiles(deleted)
        sizes.forEach { (id, size) -> updateSize(id, size) }
        appKeys.forEach { (id, appKey) -> updateAppKey(id, appKey) }
        placements.forEach { (gridWidth, rows) ->
            deletePlacements(gridWidth)
            insertPlacements(rows.filter { it.tileId !in deleted })
        }
    }

    @Insert protected abstract suspend fun insertTile(tile: TileEntity): Long

    @Insert protected abstract suspend fun insertSources(sources: List<TileSourceEntity>)

    @Query("DELETE FROM tile WHERE id IN (:ids)")
    protected abstract suspend fun deleteTiles(ids: Set<Long>)

    @Query("UPDATE tile SET size = :size WHERE id = :id")
    protected abstract suspend fun updateSize(id: Long, size: String)

    @Query("UPDATE tile SET app_key = :appKey WHERE id = :id")
    protected abstract suspend fun updateAppKey(id: Long, appKey: String)

    @Query("DELETE FROM tile_placement WHERE grid_width = :gridWidth")
    protected abstract suspend fun deletePlacements(gridWidth: Int)

    @Insert protected abstract suspend fun insertPlacements(rows: List<TilePlacementEntity>)
}
