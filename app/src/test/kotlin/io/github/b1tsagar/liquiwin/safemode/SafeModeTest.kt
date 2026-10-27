package io.github.b1tsagar.liquiwin.safemode

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.HomeActivity
import io.github.b1tsagar.liquiwin.crash.PrefsCrashStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeModeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun threeEarlyCrashesShowSafeModeUntilTheUserExitsIt() {
        PrefsCrashStore(context).earlyCrashes = 3
        ActivityScenario.launch(HomeActivity::class.java).use {
            compose.onNodeWithText("exit safe mode").assertExists()
            compose.onNodeWithText("exit safe mode").performClick()
            // The real home screen loads from Room off the main thread.
            val hint = "swipe left for your apps. long-press one and choose pin to start."
            compose.waitUntil(10_000) { compose.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, PrefsCrashStore(context).earlyCrashes)
        }
    }
}
