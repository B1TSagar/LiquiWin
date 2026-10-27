package io.github.b1tsagar.liquiwin.core.data.apps

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class IconCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val camera = LauncherApp(AppKey("com.cam", "com.cam.Main", 0), "Camera", 1L, isWorkProfile = false, isPaused = false, versionToken = "v1")

    @Test fun rendersEachIconOncePerVersion() = runTest {
        val source = FakeAppSource(icon = ColorDrawable(Color.RED))
        val cache = iconCache(source)
        cache.get(camera)
        val icon = cache.get(camera)
        assertEquals(1, source.iconLoads)
        assertFalse(icon!!.isMonochrome)
        cache.get(camera.copy(versionToken = "v2"))
        assertEquals(2, source.iconLoads)
    }

    @Test fun usesTheMonochromeLayerWhenThereIsOne() = runTest {
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.BLUE), ColorDrawable(Color.WHITE), ColorDrawable(Color.WHITE))
        assertTrue(iconCache(FakeAppSource(icon = icon)).get(camera)!!.isMonochrome)
    }

    @Test fun aMissingIconGivesNull() = runTest {
        assertNull(iconCache(FakeAppSource(icon = null)).get(camera))
    }

    private fun TestScope.iconCache(source: FakeAppSource) =
        IconCache(source, File(context.cacheDir, "icons"), densityDpi = 420, io = UnconfinedTestDispatcher(testScheduler))
}
