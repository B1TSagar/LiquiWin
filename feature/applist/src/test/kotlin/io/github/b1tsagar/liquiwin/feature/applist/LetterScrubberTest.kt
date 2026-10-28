package io.github.b1tsagar.liquiwin.feature.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterScrubberTest {
    private val labels = listOf("a", "b", "c", "d")

    @Test fun theStripIsSharedEvenlyFromTopToBottom() {
        assertEquals("a", letterAt(y = 0f, height = 400f, labels))
        assertEquals("c", letterAt(y = 250f, height = 400f, labels))
    }

    @Test fun aFingerPastEitherEndKeepsThatEndsLetter() {
        assertEquals("a", letterAt(y = -30f, height = 400f, labels))
        assertEquals("d", letterAt(y = 430f, height = 400f, labels))
    }

    @Test fun noLettersMeansNothingToScrub() {
        assertNull(letterAt(y = 100f, height = 400f, emptyList()))
    }
}
