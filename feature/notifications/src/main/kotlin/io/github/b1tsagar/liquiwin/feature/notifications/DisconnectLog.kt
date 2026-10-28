package io.github.b1tsagar.liquiwin.feature.notifications

import android.content.SharedPreferences

/** When Android stopped the listener, kept across restarts so LiquiWin can tell that it keeps happening (§3.3). */
class DisconnectLog(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    /** Counts a disconnect. One within a minute of the last one counted is the same one. */
    fun record() {
        val time = now()
        val times = recent(time)
        if (times.isNotEmpty() && time - times.last() < MINUTE_MS) return
        prefs.edit().putString(KEY, (times + time).joinToString(",")).apply()
    }

    /** Whether Android stopped live tiles 3 or more times in the last day. */
    fun keepsStopping(): Boolean = recent(now()).size >= 3

    // The last day's times, oldest first. A time after now, left by a clock set back, drops out too.
    private fun recent(now: Long) = prefs.getString(KEY, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it in 0..DAY_MS }

    private companion object {
        const val KEY = "disconnects"
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
