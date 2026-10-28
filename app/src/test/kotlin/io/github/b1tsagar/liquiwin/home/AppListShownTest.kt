package io.github.b1tsagar.liquiwin.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListShownTest {
    @Test fun rowsStayOutWhileASwipeToTheAppListIsHeld() {
        assertFalse(appListShown(shown = false, dragging = true, targetPage = 1, settledPage = 0))
    }

    @Test fun rowsComeInWhenTheSwipeLetsGoTowardTheAppList() {
        assertTrue(appListShown(shown = false, dragging = false, targetPage = 1, settledPage = 0))
    }

    @Test fun rowsStayInUntilStartHasSettled() {
        assertTrue(appListShown(shown = true, dragging = false, targetPage = 0, settledPage = 1))
        assertFalse(appListShown(shown = true, dragging = false, targetPage = 0, settledPage = 0))
    }
}
