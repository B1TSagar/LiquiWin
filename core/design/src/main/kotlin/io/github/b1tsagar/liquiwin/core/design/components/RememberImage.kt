package io.github.b1tsagar.liquiwin.core.design.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [file]'s image, decoded off the main thread. Null while it decodes, and for a file that's gone or won't decode. Keyed
 * by the file, so a new one never shows the last one's image.
 */
@Composable
fun rememberImage(file: File): ImageBitmap? {
    val image by key(file) {
        produceState<ImageBitmap?>(null) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }?.asImageBitmap() }
    }
    return image
}
