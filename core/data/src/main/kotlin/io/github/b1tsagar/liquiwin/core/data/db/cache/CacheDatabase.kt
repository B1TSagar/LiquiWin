package io.github.b1tsagar.liquiwin.core.data.db.cache

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Data that can be rebuilt: the cached app list, launch times, notifications and hidden chats. Auto Backup leaves `cache.db` out. */
@Database(
    entities = [
        AppEntryEntity::class, AppLaunchEntity::class, NotificationItemEntity::class, ConversationReadEntity::class,
        HiddenConversationEntity::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        // Everything here can be rebuilt, so a failed migration starts the file over instead of crashing Home.
        fun open(context: Context): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
