package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/** LiquiWin's notification access, which only the owner can grant, in Settings (§3.3). */
class NotificationAccess(context: Context) {
    private val app = context.applicationContext

    /** The listener. */
    val component = ComponentName(app, LiquiWinNotificationListener::class.java)

    fun isGranted(): Boolean = app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    /** Opens the listener's own access page or, where that page is missing, the list of apps with access. */
    fun openSettings(from: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                from.startActivity(detail)
                return
            } catch (e: ActivityNotFoundException) {
                Log.w("NotificationAccess", "This phone has no access page for a single listener, so the list opens", e)
            }
        }
        from.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Asks Android to bind the listener again, which it does only while access is granted. */
    fun requestRebind() {
        if (!isGranted()) return
        try {
            NotificationListenerService.requestRebind(component)
        } catch (e: RuntimeException) {
            Log.w("NotificationAccess", "Android wouldn't rebind the listener", e)
        }
    }
}
