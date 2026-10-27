package io.github.b1tsagar.liquiwin.core.data.apps

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.cache.AppEntryEntity
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val camera = AppActivity(AppKey("com.cam", "com.cam.Main", 0), "Camera", 1L, "v1")
    private val maps = AppActivity(AppKey("com.maps", "com.maps.Main", 0), "maps", 2L, "v1")
    private val calculator = AppActivity(AppKey("com.calc", "com.calc.Main", 0), "Calculator", 3L, "v1")

    @After fun tearDown() = db.close()

    @Test fun showsTheCachedListUntilTheFirstRefreshFinishes() = runTest {
        db.appDao().replaceEntries(listOf(AppEntryEntity(camera.key.flatten(), "Camera", 1L, isWork = false, versionToken = "v1")))
        val source = FakeAppSource(activities = listOf(camera, maps), gate = CompletableDeferred())
        val repo = repository(source)
        assertEquals(listOf("Camera"), repo.apps.first { it.isNotEmpty() }.map { it.label })
        assertFalse(repo.isFresh.value)
        source.gate!!.complete(Unit)
        repo.isFresh.first { it }
        assertEquals(listOf("Camera", "maps"), repo.apps.value.map { it.label })
    }

    @Test fun refreshesWhenLauncherAppsReportsAChange() = runTest {
        val source = FakeAppSource(activities = listOf(camera))
        val repo = repository(source).also { it.isFresh.first { fresh -> fresh } }
        source.activities = listOf(camera, maps)
        source.changes.emit(Unit)
        repo.apps.first { it.size == 2 }
    }

    @Test fun sortsLabelsWithTheLocaleCollator() = runTest {
        val repo = repository(FakeAppSource(activities = listOf(maps, camera, calculator))).also { it.isFresh.first { f -> f } }
        assertEquals(listOf("Calculator", "Camera", "maps"), repo.apps.value.map { it.label })
    }

    @Test fun launchingRecordsTheTime() = runTest {
        val source = FakeAppSource(activities = listOf(camera))
        val repo = repository(source, now = { 5_000L }).also { it.isFresh.first { f -> f } }
        repo.launch(camera.key)
        assertEquals(listOf(camera.key), source.launched)
        assertEquals(5_000L, repo.lastLaunched.first { it.isNotEmpty() }[camera.key])
    }

    @Test fun appsInAPausedProfileAreMarkedPaused() = runTest {
        val slack = AppActivity(AppKey("com.slack", "com.slack.Main", 10), "Slack", 1L, "v1")
        val profiles = listOf(AppProfile(0, isWork = false, isPaused = false), AppProfile(10, isWork = true, isPaused = true))
        val repo = repository(FakeAppSource(profiles = profiles, activities = listOf(camera, slack))).also { it.isFresh.first { f -> f } }
        val app = repo.apps.value.single { it.label == "Slack" }
        assertTrue(app.isWorkProfile && app.isPaused)
    }

    private fun TestScope.repository(source: FakeAppSource, now: () -> Long = { 0L }) = AppRepository(
        source, db, IconCache(source, File(context.cacheDir, "icons"), densityDpi = 420),
        backgroundScope, io = UnconfinedTestDispatcher(testScheduler), nowMillis = now,
    )
}
