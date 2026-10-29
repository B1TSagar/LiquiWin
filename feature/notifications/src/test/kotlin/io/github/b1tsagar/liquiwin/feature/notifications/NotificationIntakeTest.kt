package io.github.b1tsagar.liquiwin.feature.notifications

import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.data.db.cache.StoredItem
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntakeTest {
    @get:Rule val folder = TemporaryFolder()
    private val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val dao = db.notificationDao()
    private val actions = LiveActionCache()
    private val images by lazy { ImageStore(context, folder.newFolder("images")) }
    private val normaliser = Normaliser(context) { 0L }
    private var clock = 10_000L

    @After fun tearDown() = db.close()

    private fun TestScope.intake(normalise: (StatusBarNotification) -> Normalised? = normaliser::normalise) =
        NotificationIntake(normalise, dao, actions, images, backgroundScope, now = { clock })

    // Events are handled in order, so seeing a later event's result shows the earlier ones were handled.
    private suspend fun items(source: String = "app:com.whatsapp@0", until: (List<StoredItem>) -> Boolean) = dao.observeItems(source).first(until)

    @Test fun aPostedChatIsStoredWithItsLiveActions() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        intake().posted(chat)
        val stored = items { it.size == 2 }
        assertEquals(listOf("see you at 6", "are you coming?"), stored.map { it.item.text })
        assertTrue(stored.all { it.unread && it.item.avatarFile != null })
        assertEquals("Reply", actions.actions.value.getValue(chat.key).reply?.title)
    }

    @Test fun aMissedCallsButtonsAreKeptWithItsLiveActions() = runTest {
        val call = sbn("com.samsung.android.dialer", missedCall())
        intake().posted(call)
        items("app:com.samsung.android.dialer@0") { it.size == 1 }
        assertEquals(listOf("Call back", "Message"), actions.actions.value.getValue(call.key).buttons.map { it.title.toString() })
    }

    @Test fun aPhotoLiquiWinCantReadIsStoredAsOne() = runTest {
        intake().posted(sbn("org.telegram.messenger", telegramPhoto())) // nothing grants LiquiWin its provider
        val stored = items("app:org.telegram.messenger@0") { it.size == 1 }.single().item
        assertTrue(stored.photoUnreadable)
        assertNull(stored.imageFile)
    }

    @Test fun aPhotoLiquiWinCanReadIsStoredAsAnImage() = runTest {
        intake().posted(sbn("com.instagram.android", instagramLike()))
        val stored = items("app:com.instagram.android@0") { it.size == 1 }.single().item
        assertFalse(stored.photoUnreadable)
        assertNotNull(stored.imageFile)
    }

    // Review Focus 3
    @Test fun aRemovalRightAfterItsPostLeavesTheItemRemoved() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        intake().run { posted(chat); removed(chat.key) }
        val stored = items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
        assertTrue(stored.none { it.unread })
        assertFalse(chat.key in actions.actions.value)
    }

    // Review Focus 2
    @Test fun reconnectingStoresWhatIsShowingAndRemovesWhatWentWhileDisconnected() = runTest {
        val gone = sbn("com.whatsapp", whatsAppChat(), id = 1)
        val showing = sbn("com.whatsapp", whatsAppGroup(), id = 2)
        val intake = intake()
        intake.posted(gone)
        items { it.size == 2 }
        intake.synced(listOf(showing))
        val stored = items { it.size == 4 && it.any { s -> s.item.removedAt != null } }
        assertEquals(setOf("Mum", "Dad"), stored.filter { it.unread }.map { it.item.sender }.toSet())
        assertEquals(setOf(showing.key), actions.actions.value.keys)
    }

    // Review Focus 3
    @Test fun aRepostSavesImagesOnlyForNewMessages() = runTest {
        val intake = intake()
        intake.posted(sbn("com.whatsapp", whatsAppChat(listOf("are you coming?" to 1_000L))))
        val first = items { it.size == 1 }.single().item.avatarFile
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        val stored = items { it.size == 2 }
        assertEquals(first, stored.single { it.item.text == "are you coming?" }.item.avatarFile)
        assertEquals(2, images.dir.list()!!.size)
    }

    @Test fun messagesOlderThan24HoursAreNotStored() = runTest {
        clock = 1_500 + NotificationIntake.RETENTION_MS // "are you coming?" (1 000) is outside the window
        intake().posted(sbn("com.whatsapp", whatsAppChat()))
        assertEquals(listOf("see you at 6"), items { it.isNotEmpty() }.map { it.item.text })
    }

    // A message dated hours ahead would stay the newest for good, and reading it would mark every later one read.
    @Test fun messagesDatedHoursAheadOfTheClockAreNotStored() = runTest {
        val chat = whatsAppChat(listOf("are you coming?" to 1_000L, "see you at 6" to clock + 60_000L, "from a broken clock" to clock + 2 * 60 * 60 * 1000L))
        intake().posted(sbn("com.whatsapp", chat))
        assertEquals(listOf("see you at 6", "are you coming?"), items { it.isNotEmpty() }.map { it.item.text })
    }

    // Review Focus 1
    @Test fun aNotificationThatFailsIsSkippedAndTheNextOneIsStored() = runTest {
        val intake = intake { if (it.packageName == "com.example.bad") error("unreadable") else normaliser.normalise(it) }
        intake.posted(sbn("com.example.bad", whatsAppChat()))
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        items { it.size == 2 }
        assertEquals(emptyList<StoredItem>(), dao.observeItems("app:com.example.bad@0").first())
    }

    @Test fun cleanUpDeletesTheImagesOfDeletedItemsAndStrayFiles() = runTest {
        val intake = intake()
        intake.posted(sbn("com.whatsapp", whatsAppChat()))
        items { it.size == 2 }
        File(images.dir, "stray.webp").writeText("left by a write that failed")
        clock += NotificationIntake.RETENTION_MS
        intake.cleanUp()
        intake.posted(sbn("com.whatsapp", whatsAppChat(listOf("later" to clock)), id = 2)) // handled after the clean-up
        items { it.map { s -> s.item.text } == listOf("later") }
        assertEquals(1, images.dir.list()!!.size)
    }
}
