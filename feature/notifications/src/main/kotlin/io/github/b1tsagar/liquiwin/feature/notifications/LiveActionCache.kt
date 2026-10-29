package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Notification
import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** [buttons] are a missed call's (§5.1); a reply button among them is [reply] itself. */
data class LiveActions(val contentIntent: PendingIntent?, val reply: Notification.Action?, val buttons: List<Notification.Action> = emptyList())

/** The actions of the notifications showing now, by notification key. Memory only: a PendingIntent can't be stored. */
class LiveActionCache {
    private val state = MutableStateFlow<Map<String, LiveActions>>(emptyMap())
    val actions: StateFlow<Map<String, LiveActions>> = state.asStateFlow()

    fun put(notificationKey: String, live: LiveActions) {
        state.update { it + (notificationKey to live) }
    }

    fun remove(notificationKey: String) {
        state.update { it - notificationKey }
    }

    fun retainOnly(notificationKeys: Set<String>) {
        state.update { it.filterKeys { key -> key in notificationKeys } }
    }
}
