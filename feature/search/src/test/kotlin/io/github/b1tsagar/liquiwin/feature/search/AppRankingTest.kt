package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.testing.testApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRankingTest { // Review Focus 3
    private fun rank(q: String, vararg labels: String, launched: Map<String, Long> = emptyMap()): List<String> {
        val apps = labels.map { testApp(it) }
        return rankApps(q, apps, launched.mapKeys { (label, _) -> apps.single { it.label == label }.key }).map { it.label }
    }

    @Test fun tiersArePrefixThenWordStartThenInitialsThenContains() =
        assertEquals(listOf("Mail", "Maps", "Google Maps", "Gmail"), rank("ma", "Gmail", "Maps", "Google Maps", "Mail", "Clock"))

    @Test fun initialsFindCamelCaseNames() =
        assertEquals(listOf("WhatsApp", "Hardware"), rank("wa", "Hardware", "WhatsApp", "Twitter"))

    @Test fun tiesGoToTheMostRecentlyLaunched() =
        assertEquals(listOf("Camera", "Calendar", "Calculator"),
            rank("ca", "Calculator", "Calendar", "Camera", launched = mapOf("Camera" to 300L, "Calendar" to 200L)))

    @Test fun matchingIgnoresCaseAndAccents() =
        assertEquals(listOf("Café Bustelo"), rank("CAFE", "Café Bustelo", "Chrome"))

    @Test fun aBlankQueryFindsNothing() = assertEquals(emptyList<String>(), rank("  ", "Camera"))
}
