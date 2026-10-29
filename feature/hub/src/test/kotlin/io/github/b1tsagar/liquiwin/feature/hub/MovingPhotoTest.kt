package io.github.b1tsagar.liquiwin.feature.hub

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
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
import org.junit.Assert.assertNotNull
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

    // Walks the window's accessibility tree as TalkBack does: Compose's test tree can't show an Android view hiding the photo.
    @Test fun talkBackReachesThePhotoAndItsPlayButton() {
        show()
        val window = compose.activity.window.decorView
        val nodes = window.createAccessibilityNodeInfo().apply { setQueryFromAppProcessEnabled(window, true) }.withDescendants().toList()
        val photo = nodes.firstOrNull { it.contentDescription?.toString() == "photo" }
        assertNotNull("TalkBack sees ${nodes.map { "${it.className} ${it.contentDescription}" }}", photo)
        assertTrue(photo!!.isClickable)
        assertEquals("play", photo.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label?.toString())
        assertEquals("playing", photo.stateDescription?.toString())
        // The view under it is hidden too, so a finger exploring the photo finds the photo rather than the view.
        assertEquals(0, photo.childCount)
    }

    private fun AccessibilityNodeInfo.withDescendants(): Sequence<AccessibilityNodeInfo> =
        sequenceOf(this) + (0 until childCount).asSequence().mapNotNull { getChild(it) }.flatMap { it.withDescendants() }
}
