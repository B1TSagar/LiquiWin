package io.github.b1tsagar.liquiwin.home

import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.design.theme.LiquiWinTheme
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.FakeTileStore
import io.github.b1tsagar.liquiwin.core.testing.MainDispatcherRule
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.feature.notifications.AndroidItemLauncher
import io.github.b1tsagar.liquiwin.feature.notifications.AppNotificationSource
import io.github.b1tsagar.liquiwin.feature.notifications.ImageStore
import io.github.b1tsagar.liquiwin.feature.notifications.LiveActionCache
import io.github.b1tsagar.liquiwin.feature.notifications.NotificationIntake
import io.github.b1tsagar.liquiwin.feature.notifications.Normaliser
import io.github.b1tsagar.liquiwin.feature.start.StartScreen
import io.github.b1tsagar.liquiwin.feature.start.StartViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTileEndToEndTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    // The real pipeline from a posted notification to the tile. The fixtures live in another module's tests, so this builds its own.
    @Test fun aPostedMessageReachesItsTile() {
        val context = compose.activity.applicationContext
        val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        val actions = LiveActionCache()
        val images = ImageStore(context, folder.newFolder("images"))
        val chat = testApp("Chat", packageName = "com.example.chat")
        val catalog = FakeAppCatalog(apps = listOf(chat))
        val sources = SourceRegistry { id ->
            id.appPackageAndSerial()?.let { (pkg, serial) -> AppNotificationSource(pkg, serial, db.notificationDao(), actions, images.dir, catalog, AndroidItemLauncher(context)) }
        }
        val intake = NotificationIntake(Normaliser(context) { 0L }::normalise, db.notificationDao(), actions, images, CoroutineScope(Dispatchers.IO))
        val viewModel = StartViewModel(FakeTileStore(), catalog, gridWidth = 6, sources).apply { pin(chat.key) }
        compose.setContent { LiquiWinTheme { StartScreen(viewModel, { null }, rememberScrollState()) } }

        val now = System.currentTimeMillis()
        val alex = Person.Builder().setName("Alex").setKey("alex").build()
        val message = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("You").build()).addMessage("see you at 6", now, alex))
            .build()
        intake.posted(StatusBarNotification("com.example.chat", "com.example.chat", 1, null, 10_123, 0, 0, message, Process.myUserHandle(), now))

        compose.waitUntil(5_000) { viewModel.live.value[1L]?.unread == 1 }
        compose.mainClock.autoAdvance = false
        val said = (1..40).map {
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("tile-1").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
        }
        assertTrue(said.any { it.orEmpty().contains("Alex: see you at 6") })
        db.close()
    }
}
