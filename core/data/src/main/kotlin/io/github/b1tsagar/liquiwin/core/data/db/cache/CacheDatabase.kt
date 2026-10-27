package io.github.b1tsagar.liquiwin.core.data.db.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Data that can be rebuilt: the cached app list and launch times. Auto Backup leaves `cache.db` out. */
@Database(entities = [AppEntryEntity::class, AppLaunchEntity::class], version = 1)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun open(context: Context): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db").build()
    }
}
