package io.github.b1tsagar.liquiwin.feature.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Notification avatars and photos, downscaled and kept in an app-private folder until their items go (§3.3). */
class ImageStore(private val context: Context, val dir: File) {
    /** Saves [icon] as WebP, scaled to fit [maxPx]. Returns the file's name, or null when the icon can't be read. */
    fun save(icon: IconCompat, maxPx: Int): String? {
        val file = File(dir, "${UUID.randomUUID()}.webp")
        try {
            val bitmap = decode(icon, maxPx) ?: return null
            dir.mkdirs()
            file.outputStream().use { check(bitmap.compress(WEBP, 85, it)) { "couldn't encode" } }
            return file.name
        } catch (e: Exception) {
            file.delete() // another app's image can be anything, so any failure just means no image
            return null
        }
    }

    fun delete(names: Collection<String>) {
        for (name in names) File(dir, name).delete()
    }

    private fun decode(icon: IconCompat, maxPx: Int): Bitmap? {
        if (icon.type == IconCompat.TYPE_URI || icon.type == IconCompat.TYPE_URI_ADAPTIVE_BITMAP) {
            // Decoded at the target size, so a huge photo never decodes in full.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, icon.uri)) { decoder, info, _ ->
                val (width, height) = fitWithin(info.size.width, info.size.height, maxPx)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val drawable = icon.loadDrawable(context) ?: return null
        val (width, height) =
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) fitWithin(drawable.intrinsicWidth, drawable.intrinsicHeight, maxPx)
            else maxPx to maxPx
        return drawable.toBitmap(width, height)
    }

    companion object {
        const val AVATAR_PX = 256
        const val IMAGE_PX = 720
    }
}

/** The size that fits [maxPx] on its longer side, never larger than the original and at least 1 × 1. */
fun fitWithin(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
    val scale = minOf(1.0, maxPx.toDouble() / maxOf(width, height))
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}

@Suppress("DEPRECATION") // WEBP is the lossy format on API 29
private val WEBP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
