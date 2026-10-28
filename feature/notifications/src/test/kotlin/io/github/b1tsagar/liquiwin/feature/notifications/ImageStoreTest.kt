package io.github.b1tsagar.liquiwin.feature.notifications

import android.graphics.Bitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { ImageStore(ApplicationProvider.getApplicationContext(), folder.newFolder("images")) }
    private fun bitmap(width: Int, height: Int) = IconCompat.createWithBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

    @Test fun fitWithinScalesTheLongerSideDownAndNeverUp() {
        assertEquals(256 to 128, fitWithin(1_000, 500, 256))
        assertEquals(19 to 256, fitWithin(300, 4_000, 256))
        assertEquals(64 to 64, fitWithin(64, 64, 256))
        assertEquals(1 to 256, fitWithin(1, 10_000, 256))
    }

    @Test fun savesABitmapIconAsAFile() {
        val name = store.save(bitmap(1_000, 500), ImageStore.AVATAR_PX)
        assertTrue(File(store.dir, name!!).length() > 0) // Robolectric's legacy graphics: check the file, not its pixels
    }

    // Review Focus 1
    @Test fun anUnreadableIconGivesNull() {
        assertNull(store.save(IconCompat.createWithContentUri("content://nowhere/1"), ImageStore.IMAGE_PX))
        assertEquals(emptyList<String>(), store.dir.list().orEmpty().toList())
    }

    @Test fun deleteRemovesTheFiles() {
        val name = store.save(bitmap(10, 10), ImageStore.AVATAR_PX)!!
        store.delete(listOf(name, "missing.webp"))
        assertFalse(File(store.dir, name).exists())
    }
}
