package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Application
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.data.db.cache.StoredItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestHost::class)
class LiquiWinNotificationListenerTest {
    private val host get() = ApplicationProvider.getApplicationContext<TestHost>()
    private val listener by lazy { Robolectric.buildService(ShowingListener::class.java).create().get() }

    @After fun tearDown() {
        ListenerConnection.set(false)
        host.close()
    }

    @Test fun postedAndRemovedNotificationsReachTheStore() = runTest {
        val chat = sbn("com.whatsapp", whatsAppChat())
        listener.onNotificationPosted(chat)
        host.items { it.size == 2 }
        listener.onNotificationRemoved(chat)
        host.items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
    }

    // Review Focus 2
    @Test fun connectingMarksTheListenerConnectedAndSyncs() = runTest {
        listener.onNotificationPosted(sbn("com.whatsapp", whatsAppChat()))
        host.items { it.size == 2 }
        listener.showing = emptyArray() // the chat went while LiquiWin wasn't listening
        listener.onListenerConnected()
        assertTrue(ListenerConnection.connected.value)
        host.items { it.isNotEmpty() && it.all { s -> s.item.removedAt != null } }
    }

    @Test fun inSafeModeTheListenerStoresNothing() = runTest {
        host.safeMode = true
        listener.onNotificationPosted(sbn("com.whatsapp", whatsAppChat()))
        listener.onListenerConnected()
        assertEquals(emptyList<StoredItem>(), host.items { true }) // with no intake, nothing was queued
    }

    @Test fun disconnectingMarksTheListenerDisconnected() {
        listener.onListenerConnected()
        listener.onListenerDisconnected()
        assertFalse(ListenerConnection.connected.value)
    }
}

/** An app with an in-memory store. In safe mode it has no intake. */
class TestHost : Application(), NotificationsHost {
    var safeMode = false
    private val db by lazy { Room.inMemoryDatabaseBuilder(this, CacheDatabase::class.java).build() }
    private val intake by lazy {
        NotificationIntake(
            Normaliser(this) { 0L }::normalise, db.notificationDao(), LiveActionCache(), ImageStore(this, File(cacheDir, "images")),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            now = { 10_000L }, // keeps the fixtures' messages inside the 24-hour window
        )
    }
    override val notificationIntake: NotificationIntake? get() = if (safeMode) null else intake
    suspend fun items(until: (List<StoredItem>) -> Boolean) = db.notificationDao().observeItems("app:com.whatsapp@0").first(until)
    fun close() = db.close()
}

class ShowingListener : LiquiWinNotificationListener() {
    var showing: Array<StatusBarNotification> = emptyArray()
    override fun getActiveNotifications(): Array<StatusBarNotification> = showing
}
