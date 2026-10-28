package io.github.b1tsagar.liquiwin.feature.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether live tiles are paused, for want of notification access, or keep being stopped by Android (§3.3). */
data class LiveTilesStatus(val paused: Boolean = false, val keepsStopping: Boolean = false)

/**
 * Keeps the listener bound: one Android stopped is bound again while access is granted. Live tiles are paused without
 * access, and keep stopping after 3 stops in a day until LiquiWin's battery use is unrestricted. The work happens in
 * [scope], never on the caller's thread.
 */
class LiveTilesHealth(
    private val isGranted: () -> Boolean,
    private val connected: StateFlow<Boolean>,
    private val rebind: () -> Unit,
    private val log: DisconnectLog,
    private val isUnrestricted: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(LiveTilesStatus())
    val status: StateFlow<LiveTilesStatus> = state.asStateFlow()
    private var resumeCheck: Job? = null

    /** LiquiWin is back on screen: the status is checked, and a listener still unbound [REBIND_GRACE_MS] later is rebound. */
    fun onResume() {
        resumeCheck?.cancel()
        resumeCheck = scope.launch {
            if (!update()) return@launch
            delay(REBIND_GRACE_MS)
            if (!connected.value && isGranted()) rebindAndCount()
        }
    }

    /** Android stopped the listener: while access is granted, it's rebound and counted. */
    fun onDisconnected() {
        scope.launch { if (isGranted()) rebindAndCount() else update() }
    }

    private fun rebindAndCount() {
        rebind()
        log.record()
        update()
    }

    // Recomputes the status, and says whether access is granted.
    private fun update(): Boolean {
        val granted = isGranted()
        state.value = LiveTilesStatus(paused = !granted, keepsStopping = granted && log.keepsStopping() && !isUnrestricted())
        return granted
    }

    companion object {
        const val REBIND_GRACE_MS = 2_000L
    }
}
