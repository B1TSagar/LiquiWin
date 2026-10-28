package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import android.widget.Toast

/** How a source opens what an item points at. Each call says whether it worked. */
interface ItemLauncher {
    fun send(intent: PendingIntent): Boolean
    fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean
    fun appGone()
}

class AndroidItemLauncher(private val context: Context) : ItemLauncher {
    override fun send(intent: PendingIntent): Boolean {
        // LiquiWin is in front when a tile is tapped, so the app it opens may start from LiquiWin's visibility.
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        } else if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return try {
            intent.send(context, 0, null, null, null, null, options.toBundle())
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    // Only the Home app may start another app's shortcuts.
    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean = try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = context.getSystemService(UserManager::class.java).getUserForSerialNumber(userSerial)
        if (user == null || !launcherApps.hasShortcutHostPermission()) {
            false
        } else {
            launcherApps.startShortcut(packageName, shortcutId, null, null, user)
            true
        }
    } catch (e: RuntimeException) {
        Log.w("ItemLauncher", "Can't start a shortcut of $packageName", e) // no such shortcut, or a locked profile
        false
    }

    override fun appGone() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "this app isn't installed any more", Toast.LENGTH_SHORT).show()
        }
    }
}
