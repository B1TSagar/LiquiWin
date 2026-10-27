package io.github.b1tsagar.liquiwin.crash

import android.content.Context

/** Keeps the count in SharedPreferences, read on every access and written with commit(): a crashing process dies next. */
class PrefsCrashStore(context: Context) : CrashStore {
    private val prefs = context.getSharedPreferences("crash_guard", Context.MODE_PRIVATE)

    override var earlyCrashes: Int
        get() = prefs.getInt(KEY, 0)
        set(value) {
            prefs.edit().putInt(KEY, value).commit()
        }

    private companion object {
        const val KEY = "early_crashes"
    }
}
