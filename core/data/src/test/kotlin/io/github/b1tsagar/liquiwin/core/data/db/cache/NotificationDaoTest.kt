package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val whatsapp = "app:com.whatsapp@0"
    private val telegram = "app:org.telegram.messenger@0"

    @After fun tearDown() = db.close()

    private fun item(
        key: String, at: Long, conversation: String = "alex", notification: String = "n1", source: String = whatsapp,
        self: Boolean = false, avatar: String? = null, text: String? = "hi",
    ) = NotificationItemEntity(key, notification, source, conversation, null, null, "Alex", text, avatar, null, at, null, self)

    private suspend fun unread(source: String = whatsapp) = dao.observeItems(source).first().filter { it.unread }.map { it.item.key }

    private fun hidden(conversation: String, name: String, at: Long, source: String = whatsapp) =
        HiddenConversationEntity(source, conversation, name, at)

    @Test fun aMessageIsUnreadUntilItsConversationIsReadUpToIt() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("b", 200)))
        assertEquals(listOf("b", "a"), unread())
        dao.markRead(whatsapp, "alex", upTo = 100)
        assertEquals(listOf("b"), unread())
        dao.markRead(whatsapp, "alex", upTo = 50) // a read mark never moves back
        assertEquals(listOf("b"), unread())
    }

    @Test fun yourOwnMessagesAreNeverUnread() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("me", 150, self = true)))
        assertEquals(listOf("a"), unread())
    }

    // Review Focus 3
    @Test fun aRepostUpdatesInPlaceAndKeepsReadStateAndImages() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100, avatar = "a.webp")))
        dao.markRead(whatsapp, "alex", upTo = 100)
        dao.replaceNotification("n2", listOf(item("a", 100, notification = "n2"), item("b", 200, notification = "n2")))
        val a = dao.item("a")!!
        assertEquals("a.webp", a.avatarFile)
        assertEquals("n2", a.notificationKey)
        assertEquals(listOf("b"), unread())
    }

    // Review Focus 3
    @Test fun anEditedMessageReplacesTheOldOneAndCountsOnce() = runTest {
        assertEquals(emptyList<String>(), dao.replaceNotification("n1", listOf(item("a", 100, avatar = "a.webp"))))
        assertEquals(listOf("a.webp"), dao.replaceNotification("n1", listOf(item("a-edited", 100, text = "hi!"))))
        assertEquals(listOf("a-edited"), unread())
    }

    // Apps clear a chat's notification once it's read, then post under the same key; the window keeps the history (§5.7).
    @Test fun aClearedNotificationsMessagesStayWhenItsAppPostsAgain() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("b", 200)))
        dao.markRemoved("n1", now = 300)
        dao.replaceNotification("n1", listOf(item("c", 400)))
        assertEquals(listOf("a", "b", "c"), dao.observeConversation(whatsapp, "alex").first().map { it.item.key })
    }

    @Test fun removingANotificationReadsItsItemsButNotLaterOnes() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("b", 200)))
        dao.markRemoved("n1", now = 300)
        assertEquals(emptyList<String>(), unread())
        assertEquals(300L, dao.item("b")?.removedAt)
        dao.replaceNotification("n3", listOf(item("b", 200, notification = "n3"), item("c", 400, notification = "n3")))
        assertEquals(listOf("c"), unread())
        assertNull(dao.item("b")?.removedAt) // showing again, but still read
    }

    // Review Focus 2
    @Test fun reconcilingMarksRemovedEveryItemWhoseNotificationIsGone() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100)))
        dao.replaceNotification("n2", listOf(item("b", 200, conversation = "sam", notification = "n2")))
        dao.markRemovedExcept(setOf("n2"), now = 300)
        assertEquals(listOf("b"), unread())
        assertEquals(300L, dao.item("a")?.removedAt)
    }

    @Test fun cleanUpKeepsTheNewest50PerSourceWithinTheWindowAndReturnsTheDeletedFiles() = runTest {
        dao.replaceNotification("n1", (1..52).map { item("w$it", 1_000L + it, avatar = "w$it") })
        dao.replaceNotification("t1", listOf(item("old", 10, notification = "t1", source = telegram, avatar = "old")))
        dao.markRemoved("t1", now = 20) // reads Telegram's conversation up to 10
        assertEquals(setOf("w1", "w2", "old"), dao.cleanUp(cutoff = 500, perSource = 50).toSet())
        assertEquals((52 downTo 3).map { "w$it" }, dao.observeItems(whatsapp).first().map { it.item.key })
        assertEquals(50, dao.allFiles().size)
        // The old read mark went too, so an item older than it counts as unread again.
        dao.replaceNotification("t2", listOf(item("older", 5, notification = "t2", source = telegram)))
        assertEquals(listOf("older"), unread(telegram))
    }

    // Review Focus 4
    @Test fun aHiddenChatLeavesItsSourcesItemsUntilShownAgain() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100)))
        dao.replaceNotification("n2", listOf(item("f", 200, conversation = "family", notification = "n2")))
        dao.replaceNotification("t1", listOf(item("t", 300, conversation = "family", notification = "t1", source = telegram)))
        dao.hide(hidden("family", "Family", at = 400))
        assertEquals(listOf("a"), dao.observeItems(whatsapp).first().map { it.item.key })
        assertEquals(listOf("t"), unread(telegram)) // the same key in another app stays
        assertEquals(listOf("f"), dao.observeConversation(whatsapp, "family").first().map { it.item.key }) // its window still opens
        dao.showAgain(whatsapp, "family")
        assertEquals(listOf("f", "a"), unread())
    }

    @Test fun aConversationIsOldestFirstWithYoursAndRemovedOnes() = runTest {
        dao.replaceNotification("n1", listOf(item("a", 100), item("me", 150, self = true)))
        dao.markRemoved("n1", now = 300) // reads alex up to 150
        dao.replaceNotification("n2", listOf(item("b", 200, notification = "n2")))
        dao.replaceNotification("n3", listOf(item("s", 120, conversation = "sam", notification = "n3")))
        val chat = dao.observeConversation(whatsapp, "alex").first()
        assertEquals(listOf("a", "me", "b"), chat.map { it.item.key })
        assertEquals(listOf(false, false, true), chat.map { it.unread })
        assertEquals(300L, chat.first().item.removedAt)
    }

    @Test fun hiddenChatsAreNewestFirstAndHidingAgainRenamesThem() = runTest {
        dao.hide(hidden("alex", "Alex", at = 100))
        dao.hide(hidden("family", "Family", at = 200))
        dao.hide(hidden("family", "Family", at = 50, source = telegram))
        assertEquals(listOf("Family", "Alex"), dao.observeHidden(whatsapp).first().map { it.name })
        dao.hide(hidden("alex", "Alex B", at = 300))
        assertEquals(listOf("Alex B", "Family"), dao.observeHidden(whatsapp).first().map { it.name })
    }
}
