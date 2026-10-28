package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationItemEntity
import io.github.b1tsagar.liquiwin.core.testing.FakeAppCatalog
import io.github.b1tsagar.liquiwin.core.testing.testApp
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNotificationSourceTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val actions = LiveActionCache()
    private val whatsApp = testApp("WhatsApp", packageName = "com.whatsapp")
    private val workWhatsApp = testApp("WhatsApp", packageName = "com.whatsapp", userSerial = 10)
    private val catalog = FakeAppCatalog(apps = listOf(whatsApp, workWhatsApp))
    private val launcher = RecordingLauncher()
    private val source by lazy { AppNotificationSource("com.whatsapp", 0, dao, actions, folder.root, catalog, launcher) }
    private val intent = PendingIntent.getActivity(context, 0, Intent("chat"), PendingIntent.FLAG_IMMUTABLE)
    private val reply = Notification.Action.Builder(null, "Reply", intent).addRemoteInput(RemoteInput.Builder("reply").build()).build()

    @After fun tearDown() = db.close()

    private fun stored(key: String, at: Long, conversation: String = "alex", group: String? = null, notification: String = "n1", serial: Long = 0, shortcut: String? = "alex") =
        NotificationItemEntity(key, notification, "app:com.whatsapp@$serial", conversation, group, shortcut, if (group == null) "Alex" else "Mum", "hi", "$key.webp", null, at, null, false)

    @Test fun itemsAreNewestFirstWithUnreadAndCanReply() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200)))
        dao.markRead("app:com.whatsapp@0", "alex", upTo = 100)
        actions.put("n1", LiveActions(intent, reply))
        val items = source.items.first()
        assertEquals(listOf("b", "a"), items.map { it.key })
        assertEquals(listOf(true, false), items.map { it.unread })
        assertTrue(items.all { it.canReply })
        assertEquals(File(folder.root, "b.webp"), items.first().avatar?.file)
        assertEquals(Instant.ofEpochMilli(200), items.first().time)
        assertEquals(1, source.unreadCount.first())
    }

    @Test fun aGroupMessageIsTitledWithSenderAndGroup() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100, conversation = "family", group = "Family")))
        assertEquals("Mum · Family", source.items.first().single().title)
    }

    @Test fun openingAnItemReadsItAndSendsItsContentIntent() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200)))
        actions.put("n1", LiveActions(intent, reply = null))
        source.open(source.items.first().first())
        assertEquals(listOf(intent), launcher.sent)
        assertEquals(0, source.unreadCount.first())
    }

    @Test fun aCancelledContentIntentFallsBackToTheShortcut() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply = null))
        launcher.sendWorks = false
        source.open(source.items.first().single())
        assertEquals(listOf(Triple("com.whatsapp", "alex", 0L)), launcher.shortcuts)
    }

    @Test fun withNoShortcutItLaunchesTheApp() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100, shortcut = null)))
        source.open(source.items.first().single())
        assertEquals(listOf(whatsApp.key), catalog.launched)
    }

    @Test fun withNothingToOpenItSaysTheAppIsGone() = runTest {
        catalog.apps.value = emptyList()
        dao.replaceNotification("n1", listOf(stored("a", 100, shortcut = null)))
        source.open(source.items.first().single())
        assertEquals(1, launcher.goneCount)
    }

    @Test fun openingTheSourceLaunchesTheApp() = runTest {
        source.open(null)
        assertEquals(listOf(whatsApp.key), catalog.launched)
    }

    @Test fun markReadReadsEachConversationUpToItsNewestGivenItem() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("b", 200), stored("c", 300)))
        dao.replaceNotification("n2", listOf(stored("s", 150, conversation = "sam", notification = "n2")))
        source.markRead(source.items.first().filter { it.key in setOf("b", "s") })
        assertEquals(listOf("c"), source.items.first().filter { it.unread }.map { it.key })
    }

    // Review Focus 5
    @Test fun itemsOfTheOtherProfileStayOut() = runTest {
        dao.replaceNotification("w1", listOf(stored("work", 100, notification = "w1", serial = 10)))
        assertEquals(emptyList<TileItem>(), source.items.first())
        AppNotificationSource("com.whatsapp", 10, dao, actions, folder.root, catalog, launcher).open(null)
        assertEquals(listOf(workWhatsApp.key), catalog.launched)
    }
}

private class RecordingLauncher : ItemLauncher {
    var sendWorks = true
    val sent = mutableListOf<PendingIntent>()
    val shortcuts = mutableListOf<Triple<String, String, Long>>()
    var goneCount = 0
    override fun send(intent: PendingIntent) = sendWorks.also { if (it) sent += intent }
    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long) = true.also { shortcuts += Triple(packageName, shortcutId, userSerial) }
    override fun appGone() { goneCount++ }
}
