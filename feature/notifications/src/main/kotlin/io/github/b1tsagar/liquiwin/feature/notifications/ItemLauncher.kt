package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import android.widget.Toast

/** How a source opens what an item points at, and replies through it. Each call says whether it worked. */
interface ItemLauncher {
    fun send(intent: PendingIntent): Boolean
    fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean
    fun appGone()

    /** Fills [action]'s free-form inputs with [text] and sends it. False if it takes no text or was cancelled. */
    fun reply(action: Notification.Action, text: String): Boolean

    /** Removes the notification from Android's panel, as a swipe there would (§5.5). Nothing while the listener is unbound. */
    fun cancel(notificationKey: String)
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

    // No ActivityOptions: reply actions are broadcasts or services, not activities.
    override fun reply(action: Notification.Action, text: String): Boolean {
        val inputs = action.remoteInputs.orEmpty().filter { it.allowFreeFormInput }.toTypedArray()
        val pending = action.actionIntent
        if (inputs.isEmpty() || pending == null) return false
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs, intent, Bundle().apply { for (input in inputs) putCharSequence(input.resultKey, text) })
        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            pending.send(context, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    override fun cancel(notificationKey: String) = ListenerConnection.cancel(notificationKey)
}
