package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Bitmap

data class AppProfile(val userSerial: Long, val isWork: Boolean, val isPaused: Boolean)

data class LauncherApp(
    val key: AppKey,
    val label: String,
    val firstInstallTime: Long,
    val isWorkProfile: Boolean,
    val isPaused: Boolean,
    /** Changes whenever the app is updated. */
    val versionToken: String,
)

data class AppIcon(val bitmap: Bitmap, val isMonochrome: Boolean)
