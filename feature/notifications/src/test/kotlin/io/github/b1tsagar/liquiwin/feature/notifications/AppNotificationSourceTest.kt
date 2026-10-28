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
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun aChatIsItsStoredMessagesOldestFirstWithYours() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100), stored("me", 150).copy(sender = "You", isSelf = true), stored("b", 200)))
        dao.replaceNotification("n2", listOf(stored("s", 120, conversation = "sam", notification = "n2")))
        val chat = source.chat("alex").first()!!
        assertEquals(listOf("a", "me", "b"), chat.messages.map { it.item.key })
        assertEquals(listOf(false, true, false), chat.messages.map { it.isSelf })
        assertEquals("Alex", chat.title)
        assertEquals(ImageRef(File(folder.root, "b.webp")), chat.avatar) // the newest incoming message's
        assertFalse(chat.isGroup)
    }

    @Test fun aGroupChatIsNamedAfterTheGroupWithItsSendersAndNoAvatar() = runTest {
        dao.replaceNotification("n1", listOf(stored("m", 100, conversation = "family", group = "Family")))
        val chat = source.chat("family").first()!!
        assertEquals("Family", chat.title)
        assertTrue(chat.isGroup)
        assertNull(chat.avatar)
        assertEquals("Mum", chat.messages.single().sender)
    }

    @Test fun aChatWithNothingStoredIsNull() = runTest {
        assertNull(source.chat("alex").first())
    }

    @Test fun aChatOfOnlyYourMessagesIsNamedAfterTheApp() = runTest {
        dao.replaceNotification("n1", listOf(stored("me", 100).copy(sender = "You", isSelf = true)))
        assertEquals("WhatsApp", source.chat("alex").first()!!.title)
    }

    // Review Focus 2: after a launcher restart the cache is empty, so nothing takes replies until the app posts again
    @Test fun aChatRepliesThroughItsNewestNotificationWithALiveReplyAction() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("b", 200, notification = "n2")))
        assertNull(source.chat("alex").first()!!.replyTo)
        actions.put("n1", LiveActions(intent, reply))
        assertEquals("a", source.chat("alex").first()!!.replyTo?.key)
        actions.put("n2", LiveActions(intent, reply))
        assertEquals("b", source.chat("alex").first()!!.replyTo?.key)
    }

    @Test fun aReplyGoesToItsItemsLiveReplyAction() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        assertEquals(ReplyResult.Sent, source.reply(source.chat("alex").first()!!.replyTo!!, "on my way"))
        assertEquals(listOf(reply to "on my way"), launcher.replies)
    }

    // Review Focus 2
    @Test fun aReplyWithoutALiveActionIsActionGone() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        val item = source.chat("alex").first()!!.replyTo!!
        actions.remove("n1")
        assertEquals(ReplyResult.ActionGone, source.reply(item, "hi"))
        actions.put("n1", LiveActions(intent, reply))
        dao.markRemoved("n1", now = 300)
        assertEquals(ReplyResult.ActionGone, source.reply(item, "hi"))
        assertEquals(emptyList<Pair<Notification.Action, String>>(), launcher.replies)
    }

    // Review Focus 2
    @Test fun aReplyThatCantBeSentIsActionGone() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        actions.put("n1", LiveActions(intent, reply))
        launcher.replyWorks = false
        assertEquals(ReplyResult.ActionGone, source.reply(source.chat("alex").first()!!.replyTo!!, "hi"))
    }

    // Review Focus 3 and 4
    @Test fun hidingAChatTakesItOffTheTileAndShowingItAgainBringsItBack() = runTest {
        val work = AppNotificationSource("com.whatsapp", 10, dao, actions, folder.root, catalog, launcher)
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("m", 200, conversation = "family", group = "Family", notification = "n2")))
        dao.replaceNotification("w1", listOf(stored("w", 300, conversation = "family", group = "Family", notification = "w1", serial = 10)))
        source.hide(source.chat("family").first()!!)
        assertEquals(listOf("a"), source.items.first().map { it.key })
        assertEquals(1, source.unreadCount.first())
        assertEquals(listOf(HiddenChat(source.id, "family", "Family")), source.hiddenChats.first())
        assertEquals(listOf("w"), work.items.first().map { it.key }) // the work profile's "family" stays
        source.showAgain("family")
        assertEquals(listOf("m", "a"), source.items.first().map { it.key })
        assertEquals(emptyList<HiddenChat>(), source.hiddenChats.first())
    }

    // Final review: a plain notification's slot can hold an app's unrelated posts, so only chats and groups are hidden
    @Test fun onlyAChatOrGroupCanBeHidden() = runTest {
        dao.replaceNotification("n1", listOf(stored("a", 100)))
        dao.replaceNotification("n2", listOf(stored("p", 100, conversation = "n2", notification = "n2", shortcut = null)))
        dao.replaceNotification("n3", listOf(stored("m", 100, conversation = "n3", group = "Family", notification = "n3", shortcut = null)))
        dao.replaceNotification("n4", listOf(stored("me", 100, conversation = "n4", notification = "n4", shortcut = null).copy(sender = "You", isSelf = true)))
        assertTrue(source.chat("alex").first()!!.canHide) // keyed by its shortcut
        assertFalse(source.chat("n2").first()!!.canHide) // a plain notification, keyed by itself
        assertTrue(source.chat("n3").first()!!.canHide) // a group
        assertTrue(source.chat("n4").first()!!.canHide) // holds a message of yours
    }
}

private class RecordingLauncher : ItemLauncher {
    var sendWorks = true
    var replyWorks = true
    val sent = mutableListOf<PendingIntent>()
    val shortcuts = mutableListOf<Triple<String, String, Long>>()
    val replies = mutableListOf<Pair<Notification.Action, String>>()
    var goneCount = 0
    override fun send(intent: PendingIntent) = sendWorks.also { if (it) sent += intent }
    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long) = true.also { shortcuts += Triple(packageName, shortcutId, userSerial) }
    override fun appGone() { goneCount++ }
    override fun reply(action: Notification.Action, text: String) = replyWorks.also { if (it) replies += action to text }
}
