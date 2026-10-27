package io.github.b1tsagar.liquiwin

import android.app.Application
import android.os.StrictMode

class LiquiWinApp : Application() {
    /** Built on first use, so nothing opens a database before it's needed. */
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }
}
