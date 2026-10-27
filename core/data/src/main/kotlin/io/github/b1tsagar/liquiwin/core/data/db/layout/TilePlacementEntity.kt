package io.github.b1tsagar.liquiwin.core.data.db.layout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/** Where a tile sits in a grid `grid_width` units wide. One row per tile per width that has been arranged. */
@Entity(
    tableName = "tile_placement",
    primaryKeys = ["tile_id", "grid_width"],
    foreignKeys = [ForeignKey(entity = TileEntity::class, parentColumns = ["id"], childColumns = ["tile_id"], onDelete = ForeignKey.CASCADE)],
)
data class TilePlacementEntity(
    @ColumnInfo(name = "tile_id") val tileId: Long,
    @ColumnInfo(name = "grid_width") val gridWidth: Int,
    val x: Int,
    val y: Int,
)
