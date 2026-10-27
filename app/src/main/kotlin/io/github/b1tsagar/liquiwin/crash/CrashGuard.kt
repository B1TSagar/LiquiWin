package io.github.b1tsagar.liquiwin.crash

interface CrashStore {
    var earlyCrashes: Int
}

/**
 * Counts processes in a row that crashed within a minute of starting. Three of them start the next process in safe
 * mode, which lasts until the user leaves it. A process that lives past its first minute resets the count.
 */
class CrashGuard(private val store: CrashStore, private val uptimeMillis: () -> Long) {
    fun shouldStartInSafeMode(): Boolean = store.earlyCrashes >= SAFE_MODE_AFTER

    fun onUncaughtException() {
        if (uptimeMillis() < START_WINDOW_MILLIS) store.earlyCrashes += 1
    }

    fun onSurvivedStartWindow() {
        if (!shouldStartInSafeMode()) store.earlyCrashes = 0
    }

    fun exitSafeMode() {
        store.earlyCrashes = 0
    }

    companion object {
        const val START_WINDOW_MILLIS = 60_000L
        private const val SAFE_MODE_AFTER = 3
    }
}
