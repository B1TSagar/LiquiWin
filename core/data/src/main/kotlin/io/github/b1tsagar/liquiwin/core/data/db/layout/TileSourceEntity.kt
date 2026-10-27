package io.github.b1tsagar.liquiwin.core.data.db.layout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One source feeding a tile. The spec calls `sort_order` "order", which is an SQL keyword. */
@Entity(
    tableName = "tile_source",
    primaryKeys = ["tile_id", "source_id"],
    foreignKeys = [ForeignKey(entity = TileEntity::class, parentColumns = ["id"], childColumns = ["tile_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("source_id")],
)
data class TileSourceEntity(
    @ColumnInfo(name = "tile_id") val tileId: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
