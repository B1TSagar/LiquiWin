package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App icons as [sizePx] bitmaps: an in-memory LRU of up to 32 MB, then PNGs in [dir], then rendering. A file is
 * named after the app and its version, so an update renders afresh.
 */
// shortcut: PNGs of old app versions stay in dir until the system clears the cache; prune them if it grows large.
class IconCache(
    private val source: AppSource,
    private val dir: File,
    private val densityDpi: Int,
    private val sizePx: Int = 256,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val memory = object : LruCache<String, AppIcon>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppIcon) = value.bitmap.allocationByteCount
    }
    private val writes = CoroutineScope(SupervisorJob() + io)

    suspend fun get(app: LauncherApp): AppIcon? = withContext(io) {
        val name = sha1(app.key.flatten() + app.versionToken)
        (memory.get(name) ?: readPng(name) ?: render(app, name))?.also { memory.put(name, it) }
    }

    private fun readPng(name: String): AppIcon? {
        for (monochrome in listOf(true, false)) {
            val file = file(name, monochrome)
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return AppIcon(it, monochrome) }
        }
        return null
    }

    private fun render(app: LauncherApp, name: String): AppIcon? {
        val drawable = source.loadIcon(app.key, densityDpi) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val monochrome = if (Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) drawable.monochrome else null
        if (monochrome != null) {
            // The layer's visible 72/108 lands on the bitmap.
            monochrome.mutate().setTint(Color.WHITE)
            monochrome.setBounds(-sizePx / 4, -sizePx / 4, sizePx * 5 / 4, sizePx * 5 / 4)
            monochrome.draw(canvas)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
        val icon = AppIcon(bitmap, isMonochrome = monochrome != null)
        writes.launch { writePng(icon, file(name, icon.isMonochrome)) }
        return icon
    }

    private fun writePng(icon: AppIcon, file: File) {
        try {
            dir.mkdirs()
            val partial = File(dir, file.name + ".tmp")
            partial.outputStream().use { icon.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            partial.renameTo(file)
        } catch (e: IOException) {
            Log.w("IconCache", "Can't write ${file.name}", e)
        }
    }

    private fun file(name: String, monochrome: Boolean) = File(dir, "${name}_${if (monochrome) "m" else "c"}.png")

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
