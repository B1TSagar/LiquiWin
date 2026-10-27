package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Serves [activities] per profile, optionally held on [gate], and counts icon loads and launches. */
class FakeAppSource(
    var profiles: List<AppProfile> = listOf(AppProfile(0, isWork = false, isPaused = false)),
    var activities: List<AppActivity> = emptyList(),
    val gate: CompletableDeferred<Unit>? = null,
    var icon: Drawable? = null,
) : AppSource {
    val changes = MutableSharedFlow<Unit>()
    var iconLoads = 0
    val launched = mutableListOf<AppKey>()

    override suspend fun profiles(): List<AppProfile> = profiles

    override suspend fun activities(profile: AppProfile): List<AppActivity> {
        gate?.await()
        return activities.filter { it.key.userSerial == profile.userSerial }
    }

    override fun changes(): Flow<Unit> = changes

    override fun loadIcon(key: AppKey, densityDpi: Int): Drawable? {
        iconLoads++
        return icon
    }

    override fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?) {
        launched += key
    }

    override fun openAppInfo(key: AppKey) = Unit

    override fun uninstall(key: AppKey) = Unit

    override fun requestUnpause(userSerial: Long) = Unit
}
