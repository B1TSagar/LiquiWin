package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class AppDao {
    @Query("SELECT * FROM app_entry")
    abstract suspend fun entries(): List<AppEntryEntity>

    @Transaction
    open suspend fun replaceEntries(entries: List<AppEntryEntity>) {
        deleteEntries()
        insertEntries(entries)
    }

    @Query("SELECT * FROM app_launch")
    abstract suspend fun launches(): List<AppLaunchEntity>

    @Upsert
    abstract suspend fun upsertLaunch(launch: AppLaunchEntity)

    @Query("DELETE FROM app_entry")
    protected abstract suspend fun deleteEntries()

    @Insert
    protected abstract suspend fun insertEntries(entries: List<AppEntryEntity>)
}
