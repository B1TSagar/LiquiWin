package io.github.b1tsagar.liquiwin.feature.hub

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import io.github.b1tsagar.liquiwin.core.design.motion.animatorOn
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A photo across its bubble. A GIF or moving sticker kept as it came (§3.3) moves; a file that won't decode shows nothing. */
@Composable
internal fun Photo(file: File) {
    val drawable by produceState<Drawable?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull() }
    }
    when (val shown = drawable) {
        is AnimatedImageDrawable -> MovingPhoto(shown)
        is BitmapDrawable -> Image(shown.bitmap.asImageBitmap(), contentDescription = "photo", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

/**
 * Plays [gif] for 5 s on showing, then holds it, and for 5 s more on each tap. With Android's animations off it waits for
 * the tap. Stopping by itself keeps it within WCAG 2.2.2, so it needs no pause control (§5.7).
 */
@Composable
internal fun MovingPhoto(gif: AnimatedImageDrawable) {
    val context = LocalContext.current
    var plays by remember { mutableIntStateOf(if (animatorOn(context)) 1 else 0) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(gif, plays) {
        if (plays == 0) return@LaunchedEffect
        gif.start()
        playing = true
        try {
            delay(PLAY_MS)
        } finally {
            gif.stop()
            playing = false
        }
    }
    val state = if (playing) "playing" else null
    // TalkBack gets an AndroidView as the bare view, without its semantics, so they sit on this wrapper. Clearing the
    // view's own keeps the photo one whole node, which a finger exploring it finds too.
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "play", role = Role.Button) { plays++ }
            .clearAndSetSemantics {
                contentDescription = "photo"
                if (state != null) stateDescription = state
            },
    ) {
        AndroidView(
            { ImageView(it).apply { adjustViewBounds = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
            Modifier.fillMaxWidth(),
            update = { it.setImageDrawable(gif) },
        )
    }
}

private const val PLAY_MS = 5_000L
