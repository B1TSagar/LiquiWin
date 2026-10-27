package io.github.b1tsagar.liquiwin.core.data.db.layout

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The tile tables. Its own file, `layout.db`, so Auto Backup can include it and leave `cache.db` out. */
@Database(entities = [TileEntity::class, TileSourceEntity::class, TilePlacementEntity::class], version = 1)
abstract class LayoutDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao

    companion object {
        fun open(context: Context): LayoutDatabase =
            Room.databaseBuilder(context, LayoutDatabase::class.java, "layout.db").build()
    }
}
