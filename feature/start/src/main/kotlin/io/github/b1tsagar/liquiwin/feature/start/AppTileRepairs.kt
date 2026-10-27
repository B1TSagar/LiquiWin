package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.AppProfile
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import io.github.b1tsagar.liquiwin.core.tiles.model.Tile
import io.github.b1tsagar.liquiwin.core.tiles.model.TileKind

data class AppTileRepairs(val repoint: Map<Long, AppKey> = emptyMap(), val remove: Set<Long> = emptySet())

/**
 * What to do with APP tiles whose app isn't listed. For each tile the first rule that matches decides:
 * its key is listed: keep it; another activity of the same package and user is listed: repoint to the first one;
 * its profile isn't listed: remove it; its profile is paused or lists no apps (starting up or broken, not empty):
 * keep it; otherwise remove it. Without a main profile in [profiles] nothing changes.
 */
fun planAppTileRepairs(tiles: List<Tile>, apps: List<LauncherApp>, profiles: List<AppProfile>): AppTileRepairs {
    if (profiles.none { !it.isWork }) return AppTileRepairs()
    val listed = apps.mapTo(HashSet()) { it.key }
    val repoint = mutableMapOf<Long, AppKey>()
    val remove = mutableSetOf<Long>()
    for (tile in tiles) {
        val key = tile.appKey
        if (tile.kind != TileKind.APP || key == null || key in listed) continue
        val sibling = apps.firstOrNull { it.key.packageName == key.packageName && it.key.userSerial == key.userSerial }
        val profile = profiles.firstOrNull { it.userSerial == key.userSerial }
        when {
            sibling != null -> repoint[tile.id] = sibling.key
            profile == null -> remove += tile.id
            profile.isPaused || apps.none { it.key.userSerial == key.userSerial } -> Unit
            else -> remove += tile.id
        }
    }
    return AppTileRepairs(repoint, remove)
}
