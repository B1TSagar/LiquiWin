package io.github.b1tsagar.liquiwin.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    private val store = object : CrashStore { override var earlyCrashes = 0 }
    private var uptime = 5_000L
    private val guard = CrashGuard(store) { uptime }

    @Test fun threeEarlyCrashesStartSafeMode() { repeat(3) { guard.onUncaughtException() }; assertTrue(guard.shouldStartInSafeMode()) }
    @Test fun twoEarlyCrashesDoNot() { repeat(2) { guard.onUncaughtException() }; assertFalse(guard.shouldStartInSafeMode()) }
    @Test fun crashesAfterTheFirstMinuteDoNotCount() { uptime = 61_000; repeat(3) { guard.onUncaughtException() }; assertFalse(guard.shouldStartInSafeMode()) }
    @Test fun survivingTheFirstMinuteResetsTheCount() {
        repeat(2) { guard.onUncaughtException() }; guard.onSurvivedStartWindow(); guard.onUncaughtException()
        assertFalse(guard.shouldStartInSafeMode())
    }
    @Test fun exitingSafeModeResetsTheCount() { repeat(3) { guard.onUncaughtException() }; guard.exitSafeMode(); assertFalse(guard.shouldStartInSafeMode()) }

    // Safe mode lasts until the user leaves it: a safe-mode process that lives a minute doesn't end it.
    @Test fun survivingTheFirstMinuteInSafeModeKeepsSafeMode() {
        repeat(3) { guard.onUncaughtException() }; guard.onSurvivedStartWindow()
        assertTrue(guard.shouldStartInSafeMode())
    }
}
