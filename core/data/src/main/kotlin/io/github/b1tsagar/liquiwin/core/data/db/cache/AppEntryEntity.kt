package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One launchable app as last read from LauncherApps, shown at start until the first refresh. */
@Entity(tableName = "app_entry")
data class AppEntryEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    val label: String,
    @ColumnInfo(name = "first_install_time") val firstInstallTime: Long,
    @ColumnInfo(name = "is_work") val isWork: Boolean,
    @ColumnInfo(name = "version_token") val versionToken: String,
)
