package io.github.b1tsagar.liquiwin.core.data.db.layout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A tile. `kind`, `size` and `template` hold the enum names of the `:core:tiles` model. */
@Entity(tableName = "tile")
data class TileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val size: String,
    val template: String,
    val title: String? = null,
    @ColumnInfo(name = "color_override") val colorOverride: Int? = null,
    @ColumnInfo(name = "surface_override") val surfaceOverride: String? = null,
    @ColumnInfo(name = "show_previews", defaultValue = "1") val showPreviews: Boolean = true,
    @ColumnInfo(name = "keep_read_items_24h", defaultValue = "0") val keepReadItems24h: Boolean = false,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: Long? = null,
    @ColumnInfo(name = "app_widget_id") val appWidgetId: Int? = null,
    @ColumnInfo(name = "shortcut_ref") val shortcutRef: String? = null,
    /** The flattened AppKey: the spec's "package reference". */
    @ColumnInfo(name = "app_key") val appKey: String? = null,
)
