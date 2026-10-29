package io.github.b1tsagar.liquiwin.feature.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToInt

/** Notification avatars and photos, downscaled and kept in an app-private folder until their items go (§3.3). */
class ImageStore(private val context: Context, val dir: File) {
    /** Saves [icon] as WebP, scaled to fit [maxPx]. Returns the file's name, or null when the icon can't be read. */
    fun save(icon: IconCompat, maxPx: Int): String? = save(icon, maxPx, keepsMotion = false)

    /**
     * Saves a chat photo as [save] does, but keeps a GIF or moving sticker of at most [IMAGE_PX] and [MOVING_BYTES] as it
     * came, for the chat window to play (§3.3, §5.7).
     */
    fun savePhoto(icon: IconCompat): String? = save(icon, IMAGE_PX, keepsMotion = true)

    fun delete(names: Collection<String>) {
        for (name in names) File(dir, name).delete()
    }

    private fun save(icon: IconCompat, maxPx: Int, keepsMotion: Boolean): String? {
        var written: File? = null
        try {
            val uri = if (icon.type == IconCompat.TYPE_URI || icon.type == IconCompat.TYPE_URI_ADAPTIVE_BITMAP) icon.uri else null
            // A photo small enough to keep is read once, to decode it and, if it moves, to keep it as it came.
            val bytes = if (keepsMotion && uri != null) readUpTo(uri, MOVING_BYTES) else null
            var format: String? = null // a moving picture's, such as gif
            val bitmap = when {
                bytes != null -> decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxPx) { info ->
                    if (info.isAnimated && maxOf(info.size.width, info.size.height) <= maxPx) format = info.mimeType.substringAfter('/')
                }
                uri != null -> decode(ImageDecoder.createSource(context.contentResolver, uri), maxPx)
                else -> draw(icon, maxPx)
            } ?: return null
            dir.mkdirs()
            val moving = format
            written = File(dir, "${UUID.randomUUID()}.${moving ?: "webp"}")
            if (moving != null && bytes != null) written.writeBytes(bytes)
            else written.outputStream().use { check(bitmap.compress(WEBP, 85, it)) { "couldn't encode" } }
            return written.name
        } catch (e: Exception) {
            written?.delete() // another app's image can be anything, so any failure just means no image
            return null
        }
    }

    // Decoded at the target size, so a huge photo never decodes in full. [header] sees the image's size and kind first.
    private fun decode(source: ImageDecoder.Source, maxPx: Int, header: (ImageDecoder.ImageInfo) -> Unit = {}): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            header(info)
            val (width, height) = fitWithin(info.size.width, info.size.height, maxPx)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun draw(icon: IconCompat, maxPx: Int): Bitmap? {
        val drawable = icon.loadDrawable(context) ?: return null
        val (width, height) =
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) fitWithin(drawable.intrinsicWidth, drawable.intrinsicHeight, maxPx)
            else maxPx to maxPx
        return drawable.toBitmap(width, height)
    }

    // All of [uri] when it's at most [max] bytes, else null.
    private fun readUpTo(uri: Uri, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > max) return null
                out.write(buffer, 0, read)
            }
        }
        return null
    }

    companion object {
        const val AVATAR_PX = 256
        const val IMAGE_PX = 720
        const val MOVING_BYTES = 5 * 1024 * 1024
    }
}

/** The size that fits [maxPx] on its longer side, never larger than the original and at least 1 × 1. */
fun fitWithin(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
    val scale = minOf(1.0, maxPx.toDouble() / maxOf(width, height))
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}

@Suppress("DEPRECATION") // WEBP is the lossy format on API 29
private val WEBP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
