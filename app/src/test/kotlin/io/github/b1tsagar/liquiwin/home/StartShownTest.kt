package io.github.b1tsagar.liquiwin.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartShownTest {
    @Test fun tilesFollowWhereASwipeLetsGo() {
        assertFalse(startShown(shown = true, dragging = false, targetPage = 1))
        assertTrue(startShown(shown = false, dragging = false, targetPage = 0))
    }

    @Test fun tilesStayPutWhileASwipeIsHeld() {
        assertTrue(startShown(shown = true, dragging = true, targetPage = 1))
        assertFalse(startShown(shown = false, dragging = true, targetPage = 0))
    }
}
