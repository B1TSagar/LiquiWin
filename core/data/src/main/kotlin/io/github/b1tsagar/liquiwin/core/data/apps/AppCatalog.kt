package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Rect
import android.os.Bundle
import kotlinx.coroutines.flow.StateFlow

interface AppCatalog {
    /** Sorted by label (locale Collator), then key.flatten(). */
    val apps: StateFlow<List<LauncherApp>>
    val profiles: StateFlow<List<AppProfile>>

    /** True once LauncherApps has been read in this process. */
    val isFresh: StateFlow<Boolean>
    val lastLaunched: StateFlow<Map<AppKey, Long>>

    fun launch(key: AppKey, sourceBounds: Rect? = null, options: Bundle? = null)
    fun openAppInfo(key: AppKey)
    fun uninstall(key: AppKey)
    fun requestUnpause(userSerial: Long)
    suspend fun icon(app: LauncherApp): AppIcon?
}
