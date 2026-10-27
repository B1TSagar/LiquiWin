package io.github.b1tsagar.liquiwin.core.testing

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind
import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.model.TileTemplate

/** A launcher app; it's in the work profile when [userSerial] isn't 0. */
fun testApp(
    label: String,
    packageName: String = "com.example." + label.lowercase().filter(Char::isLetterOrDigit),
    className: String = "$packageName.Main",
    userSerial: Long = 0,
    firstInstallTime: Long = 0,
    isPaused: Boolean = false,
    versionToken: String = "v1",
): LauncherApp = LauncherApp(
    key = AppKey(packageName, className, userSerial),
    label = label,
    firstInstallTime = firstInstallTime,
    isWorkProfile = userSerial != 0L,
    isPaused = isPaused,
    versionToken = versionToken,
)

/** An APP tile for [key], fed by the app's notifications. */
fun appTile(id: Long, key: AppKey, size: TileSize = TileSize.MEDIUM): Tile =
    Tile(id, TileKind.APP, null, size, TileTemplate.FLIP, null, key, listOf(SourceId.forApp(key.packageName, key.userSerial)))
