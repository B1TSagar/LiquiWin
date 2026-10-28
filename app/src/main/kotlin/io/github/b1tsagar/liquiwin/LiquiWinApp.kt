package io.github.b1tsagar.liquiwin

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import io.github.b1tsagar.liquiwin.crash.CrashGuard
import io.github.b1tsagar.liquiwin.crash.PrefsCrashStore
import io.github.b1tsagar.liquiwin.feature.notifications.NotificationIntake
import io.github.b1tsagar.liquiwin.feature.notifications.NotificationsHost

class LiquiWinApp : Application(), NotificationsHost {
    lateinit var crashGuard: CrashGuard
        private set

    /** Built on first use, so nothing opens a database before it's needed, and safe mode never builds it. */
    val graph: AppGraph by lazy { AppGraph(this) }

    override val notificationIntake: NotificationIntake?
        get() = if (crashGuard.shouldStartInSafeMode()) null else graph.notificationIntake

    override fun onCreate() {
        // The guard comes first, so a crash anywhere after this counts.
        val guard = CrashGuard(PrefsCrashStore(this)) { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }
        crashGuard = guard
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                guard.onUncaughtException()
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
        val uptime = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Handler(Looper.getMainLooper()).postDelayed(guard::onSurvivedStartWindow, (CrashGuard.START_WINDOW_MILLIS - uptime).coerceAtLeast(0))

        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }
}
