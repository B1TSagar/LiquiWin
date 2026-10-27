package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.core.data.apps.AppProfile
import io.github.b1tsagar.liquiwin.core.testing.appTile
import io.github.b1tsagar.liquiwin.core.testing.testApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTileRepairsTest { // Review Focus 1
    private val main = AppProfile(0, isWork = false, isPaused = false)
    private val camera = testApp("Camera")
    private val notes = testApp("Notes")

    @Test fun keepsTilesWhoseAppIsListed() =
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, camera.key)), listOf(camera), listOf(main)))

    @Test fun followsTheAppToItsNewLauncherActivity() {
        val alias = camera.copy(key = camera.key.copy(className = "com.example.camera.AltIcon"))
        assertEquals(AppTileRepairs(repoint = mapOf(1L to alias.key)), planAppTileRepairs(listOf(appTile(1, camera.key)), listOf(alias), listOf(main)))
    }

    @Test fun keepsTilesOfAPausedProfile() {
        val slack = testApp("Slack", userSerial = 10)
        val profiles = listOf(main, AppProfile(10, isWork = true, isPaused = true))
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), profiles))
    }

    @Test fun keepsTilesOfAProfileThatListsNothing() {
        val slack = testApp("Slack", userSerial = 10)
        val profiles = listOf(main, AppProfile(10, isWork = true, isPaused = false))
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), profiles))
    }

    @Test fun removesTilesWhoseAppIsGone() =
        assertEquals(AppTileRepairs(remove = setOf(1L)), planAppTileRepairs(listOf(appTile(1, notes.key)), listOf(camera), listOf(main)))

    @Test fun removesTilesOfARemovedProfile() {
        val slack = testApp("Slack", userSerial = 10)
        assertEquals(AppTileRepairs(remove = setOf(1L)), planAppTileRepairs(listOf(appTile(1, slack.key)), listOf(camera), listOf(main)))
    }

    @Test fun changesNothingWhenTheMainProfileListIsEmpty() =
        assertEquals(AppTileRepairs(), planAppTileRepairs(listOf(appTile(1, camera.key)), emptyList(), listOf(main)))
}
