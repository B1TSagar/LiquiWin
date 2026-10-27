package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_launch")
data class AppLaunchEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "last_launched_at") val lastLaunchedAt: Long,
)
