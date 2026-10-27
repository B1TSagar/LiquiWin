package io.github.b1tsagar.liquiwin.feature.applist

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.testing.testApp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGrouperTest { // Review Focus 3
    private val grouper = AppGrouper(Locale.ENGLISH)
    private fun labels(index: AppIndex) = index.sections.associate { s -> s.label to s.apps.map { it.label } }

    @Test fun groupsByFirstLetterInCollatorOrder() =
        assertEquals(mapOf("c" to listOf("calculator", "Camera"), "z" to listOf("Zoom")),
            labels(grouper.group(listOf(testApp("Zoom"), testApp("Camera"), testApp("calculator")))))

    @Test fun digitsAndSymbolsGoUnderHash() = // the Collator puts punctuation before digits
        assertEquals(listOf("@home", "7-Zip"), labels(grouper.group(listOf(testApp("7-Zip"), testApp("@home"))))["#"])

    @Test fun accentedLettersJoinTheirBaseLetter() =
        assertEquals(listOf("Échecs"), labels(grouper.group(listOf(testApp("Échecs"))))["e"])

    @Test fun otherScriptsGetTheirOwnGroupsAfterTheLatinLetters() {
        val index = grouper.group(listOf(testApp("Zoom"), testApp("हिन्दी"), testApp("Яндекс")))
        assertEquals(listOf("z", "я", "ह"), index.sections.map { it.label })
    }

    @Test fun theLetterGridListsEveryLetterAndMarksEmptyOnes() {
        val letters = grouper.group(listOf(testApp("Camera"))).letters
        assertEquals("#", letters.first().label)
        assertTrue(letters.single { it.label == "c" }.hasApps)
        assertFalse(letters.single { it.label == "a" }.hasApps)
        assertEquals(27, letters.size)
    }

    @Test fun sameNamedPersonalAndWorkAppsBothAppearPersonalFirst() {
        val personal = testApp("Camera"); val work = testApp("Camera", userSerial = 10)
        assertEquals(listOf(personal.key, work.key), grouper.group(listOf(work, personal)).sections.single().apps.map { it.key })
    }
}
