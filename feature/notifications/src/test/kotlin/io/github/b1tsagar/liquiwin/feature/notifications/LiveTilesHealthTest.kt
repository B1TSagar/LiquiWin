package io.github.b1tsagar.liquiwin.feature.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LiveTilesHealthTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("listener_health", Context.MODE_PRIVATE)
    private var granted = true
    private var unrestricted = false
    private var rebinds = 0
    private val connected = MutableStateFlow(false)
    private fun TestScope.health() = LiveTilesHealth({ granted }, connected, { rebinds++ }, DisconnectLog(prefs) { currentTime }, { unrestricted }, backgroundScope)

    // Review Focus 4
    @Test fun withoutAccessTilesArePaused() = runTest {
        granted = false
        val health = health()
        health.onResume()
        advanceTimeBy(5_000)
        assertTrue(health.status.value.paused)
        assertEquals(0, rebinds)
    }

    @Test fun aGrantedListenerStillDisconnectedTwoSecondsAfterResumeIsReboundAndCounted() = runTest {
        val health = health()
        health.onResume()
        advanceTimeBy(1_900)
        assertEquals(0, rebinds)
        advanceTimeBy(200)
        assertEquals(1, rebinds)
        assertFalse(health.status.value.paused)
    }

    @Test fun aConnectedListenerIsLeftAlone() = runTest {
        val health = health()
        health.onResume()
        advanceTimeBy(1_000)
        connected.value = true
        advanceTimeBy(5_000)
        assertEquals(0, rebinds)
    }

    // Review Focus 4
    @Test fun aDisconnectAfterAccessIsRevokedNeitherRebindsNorCounts() = runTest {
        val health = health()
        granted = false
        repeat(3) { health.onDisconnected(); advanceTimeBy(61_000) }
        assertEquals(0, rebinds)
        assertTrue(health.status.value.paused)
        granted = true
        connected.value = true
        health.onResume()
        advanceTimeBy(3_000)
        assertEquals(LiveTilesStatus(), health.status.value)
    }

    @Test fun theGuideShowsAfterThreeDisconnectsUntilBatteryIsUnrestricted() = runTest {
        val health = health()
        repeat(2) { health.onDisconnected(); advanceTimeBy(61_000) }
        assertFalse(health.status.value.keepsStopping)
        health.onDisconnected()
        runCurrent()
        assertTrue(health.status.value.keepsStopping)
        assertEquals(3, rebinds)
        unrestricted = true
        connected.value = true
        health.onResume()
        advanceTimeBy(3_000)
        assertFalse(health.status.value.keepsStopping)
    }
}
