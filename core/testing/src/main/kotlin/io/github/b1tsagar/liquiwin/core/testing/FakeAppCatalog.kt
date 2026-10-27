package io.github.b1tsagar.liquiwin.core.testing

import android.graphics.Rect
import android.os.Bundle
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppIcon
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.AppProfile
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import kotlinx.coroutines.flow.MutableStateFlow

/** An [AppCatalog] whose flows tests set directly, and which records every call. */
class FakeAppCatalog(
    apps: List<LauncherApp> = emptyList(),
    profiles: List<AppProfile> = listOf(AppProfile(0, isWork = false, isPaused = false)),
    fresh: Boolean = true,
) : AppCatalog {
    override val apps = MutableStateFlow(apps)
    override val profiles = MutableStateFlow(profiles)
    override val isFresh = MutableStateFlow(fresh)
    override val lastLaunched = MutableStateFlow<Map<AppKey, Long>>(emptyMap())

    val launched = mutableListOf<AppKey>()
    val appInfoOpened = mutableListOf<AppKey>()
    val uninstalled = mutableListOf<AppKey>()
    val unpauseRequests = mutableListOf<Long>()

    override fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?) {
        launched += key
    }

    override fun openAppInfo(key: AppKey) {
        appInfoOpened += key
    }

    override fun uninstall(key: AppKey) {
        uninstalled += key
    }

    override fun requestUnpause(userSerial: Long) {
        unpauseRequests += userSerial
    }

    override suspend fun icon(app: LauncherApp): AppIcon? = null
}
