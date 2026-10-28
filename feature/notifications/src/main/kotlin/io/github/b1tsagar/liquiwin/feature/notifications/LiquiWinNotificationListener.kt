package io.github.b1tsagar.liquiwin.feature.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether Android has the listener bound right now. */
object ListenerConnection {
    private val state = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = state.asStateFlow()

    internal fun set(connected: Boolean) {
        state.value = connected
    }
}

/** Hands other apps' notifications to the intake. Its callbacks arrive on the main thread, so they only queue work. */
open class LiquiWinNotificationListener : NotificationListenerService() { // open so a test can set what's showing
    private val intake get() = (application as? NotificationsHost)?.notificationIntake

    override fun onListenerConnected() {
        val intake = intake ?: return
        ListenerConnection.set(true)
        try {
            intake.synced(activeNotifications.toList())
        } catch (e: RuntimeException) {
            Log.w("NotificationListener", "Can't read the notifications showing, so this connection skips the sync", e)
        }
    }

    override fun onListenerDisconnected() {
        if (intake == null) return
        ListenerConnection.set(false)
        (application as? NotificationsHost)?.liveTilesHealth?.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        intake?.posted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        intake?.removed(sbn.key)
    }
}
