package io.github.b1tsagar.liquiwin.core.design.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp

/** Draws [bitmap] at [size]. While it's null the space stays empty, so a late icon doesn't shift the layout. */
@Composable
fun IconImage(bitmap: Bitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, Modifier.size(size))
    }
}
