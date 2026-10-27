package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import kotlinx.coroutines.flow.Flow

data class AppActivity(val key: AppKey, val label: String, val firstInstallTime: Long, val versionToken: String)

/** The system's view of launchable apps. */
interface AppSource {
    suspend fun profiles(): List<AppProfile>
    suspend fun activities(profile: AppProfile): List<AppActivity>

    /** Any LauncherApps callback or profile broadcast. */
    fun changes(): Flow<Unit>
    fun loadIcon(key: AppKey, densityDpi: Int): Drawable?
    fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?)
    fun openAppInfo(key: AppKey)
    fun uninstall(key: AppKey)
    fun requestUnpause(userSerial: Long)
}
