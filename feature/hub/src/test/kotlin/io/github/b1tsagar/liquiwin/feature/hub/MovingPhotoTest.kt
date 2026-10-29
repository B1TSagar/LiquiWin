package io.github.b1tsagar.liquiwin.feature.hub

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.movingGif
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// A real moving picture needs Robolectric's native graphics, which on Windows decode bytes but not files.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovingPhotoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val gif = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(movingGif()))) as AnimatedImageDrawable

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquiWinTheme { MovingPhoto(gif) } }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun photo() = compose.onNodeWithContentDescription("photo")
    private fun said(): String? = photo().fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    @Test fun aMovingPhotoPlaysFor5SecondsThenHolds() {
        show()
        assertTrue(gif.isRunning)
        assertEquals("playing", said())
        compose.mainClock.advanceTimeBy(5_000)
        assertFalse(gif.isRunning)
        assertNull(said())
    }

    @Test fun aTapPlaysItFor5SecondsMore() {
        show()
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("play", photo().fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        photo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(gif.isRunning)
        compose.mainClock.advanceTimeBy(5_000)
        assertFalse(gif.isRunning)
    }

    @Test fun withAnimationsOffItWaitsForATap() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse(gif.isRunning)
        photo().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(gif.isRunning)
    }
}
