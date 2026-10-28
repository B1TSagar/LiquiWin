package io.github.b1tsagar.liquiwin.feature.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisconnectLogTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
    private var clock = 0L
    private val log = DisconnectLog(prefs) { clock }
    private val hour = 60 * 60 * 1000L
    private fun disconnectAt(time: Long) { clock = time; log.record() }

    @Test fun threeDisconnectsInADayMeanTilesKeepStopping() {
        disconnectAt(0); disconnectAt(hour)
        assertFalse(log.keepsStopping())
        disconnectAt(2 * hour)
        assertTrue(log.keepsStopping())
    }

    @Test fun disconnectsOlderThanADayDropOut() {
        disconnectAt(0); disconnectAt(hour); disconnectAt(2 * hour)
        clock = 24 * hour + 1
        assertFalse(log.keepsStopping())
    }

    @Test fun disconnectsWithinAMinuteCountOnce() {
        disconnectAt(0); disconnectAt(30_000); disconnectAt(59_000)
        assertFalse(log.keepsStopping())
    }

    @Test fun theCountSurvivesARestart() {
        disconnectAt(0); disconnectAt(hour); disconnectAt(2 * hour)
        assertTrue(DisconnectLog(prefs) { clock }.keepsStopping())
    }
}
