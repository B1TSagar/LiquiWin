package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NormaliserTest {
    private val work = UserHandle.getUserHandleForUid(10 * 100_000 + 10_123)
    private val normaliser = Normaliser(context) { if (it == work) 10L else 0L }
    private fun normalise(pkg: String, notification: Notification, tag: String? = null, user: UserHandle = Process.myUserHandle()) =
        normaliser.normalise(sbn(pkg, notification, tag = tag, user = user))

    @Test fun aWhatsAppChatGivesOneItemPerMessage() {
        val n = normalise("com.whatsapp", whatsAppChat())!!
        assertEquals(SourceId("app:com.whatsapp@0"), n.sourceId)
        assertEquals(listOf("are you coming?", "see you at 6"), n.items.map { it.text })
        assertEquals(listOf(1_000L, 2_000L), n.items.map { it.postedAt })
        for (item in n.items) {
            assertEquals("Alex", item.sender)
            assertEquals("alex", item.conversationKey) // the shortcut id
            assertNull(item.conversationTitle)
            assertFalse(item.isSelf)
            assertNotNull(item.avatar)
        }
        assertNotNull(n.contentIntent)
    }

    @Test fun aGroupMessageNamesItsGroup() {
        val n = normalise("com.whatsapp", whatsAppGroup())!!
        assertEquals(listOf("Mum", "Dad"), n.items.map { it.sender })
        assertEquals(listOf("Family", "Family"), n.items.map { it.conversationTitle })
    }

    @Test fun yourOwnMessagesAreMarkedAsYours() {
        val n = normalise("com.whatsapp", whatsAppChat(mine = listOf("on my way" to 3_000L)))!!
        assertEquals(listOf(false, false, true), n.items.map { it.isSelf })
    }

    @Test fun theReplyActionIsTheSemanticReplyOrAFreeFormInput() {
        assertEquals("Reply", normalise("com.whatsapp", whatsAppChat())!!.replyAction?.title) // never "Mark as read", listed first
        assertEquals("Reply", normalise("com.facebook.orca", messengerChat())!!.replyAction?.title)
        assertNull(normalise("com.google.android.gm", gmailEmail())!!.replyAction) // its Reply takes no text
    }

    @Test fun aMissedCallKeepsItsFirstTwoButtonsInTheAppsOrder() {
        val call = normalise("com.samsung.android.dialer", missedCall(listOf("Call back", "Message", "Block")))!!
        assertEquals(listOf("Call back", "Message"), call.buttons.map { it.title.toString() })
    }

    @Test fun aMissedCallsReplyButtonIsItsReplyAction() {
        val call = normalise("com.google.android.dialer", missedCall(listOf("Call back"), replyLabel = "Reply"))!!
        assertSame(call.replyAction, call.buttons[1])
    }

    @Test fun otherNotificationsHaveNoButtons() {
        assertEquals(emptyList<Notification.Action>(), normalise("com.whatsapp", whatsAppChat())!!.buttons)
    }

    @Test fun anEmailIsOnePlainItemWithItsBigText() {
        val n = normalise("com.google.android.gm", gmailEmail(), tag = "pat")!!
        val item = n.items.single()
        assertEquals("Pat Lee", item.sender)
        assertEquals("Lunch on Friday? The new place on King St.", item.text)
        assertEquals(n.notificationKey, item.conversationKey)
        assertEquals(4_000L, item.postedAt) // its `when`
    }

    @Test fun aNotificationWithoutAWhenIsDatedByItsPostTime() {
        assertEquals(5_000L, normalise("com.snapchat.android", snapchatSnap())!!.items.single().postedAt)
    }

    @Test fun summariesOngoingMediaProgressAndOwnNotificationsAreIgnored() {
        assertNull(normalise("com.whatsapp", whatsAppSummary()))
        assertNull(normalise("com.google.android.gm", gmailSummary()))
        assertNull(normalise("com.google.android.apps.maps", navigation()))
        assertNull(normalise("com.spotify.music", musicPlayer()))
        assertNull(normalise("com.android.chrome", download()))
        assertNull(normalise(context.packageName, ownNotification()))
    }

    // Review Focus 3
    @Test fun aRepostedMessageKeepsItsKeyAndAnEditedOneGetsANewKey() {
        val first = normalise("com.whatsapp", whatsAppChat(listOf("see you at 6" to 2_000L)))!!.items.single()
        val again = normaliser.normalise(sbn("com.whatsapp", whatsAppChat(), postTime = 9_000))!!.items.last()
        val edited = normalise("com.whatsapp", whatsAppChat(listOf("see you at 7" to 2_000L)))!!.items.single()
        assertEquals(first.key, again.key)
        assertNotEquals(first.key, edited.key)
    }

    @Test fun aPhotoMessageCarriesItsImage() {
        val photo = normalise("org.telegram.messenger", telegramPhoto())!!.items.single()
        assertEquals("content://org.telegram.messenger.provider/media/1", photo.image?.uri.toString())
        assertNotNull(normalise("com.instagram.android", instagramLike())!!.items.single().image)
        assertNull(normalise("com.snapchat.android", snapchatSnap())!!.items.single().image)
    }

    // Review Focus 1
    @Test fun hugeTextIsCutAndAnEmptyNotificationIsIgnored() {
        // Android's builder already trims these to 1,024 characters, so they're set the way an app that skips it would.
        val huge = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat).build()
        huge.extras.putCharSequence(Notification.EXTRA_TITLE, "A".repeat(100_000))
        huge.extras.putCharSequence(Notification.EXTRA_TEXT, "b".repeat(100_000))
        val item = normalise("com.example.spam", huge)!!.items.single()
        assertEquals(200, item.sender.length)
        assertEquals(2_000, item.text?.length)
        assertNull(normalise("com.example.empty", NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat).build()))
    }

    // Review Focus 1
    @Test fun aMalformedMessagesBundleFallsBackToThePlainNotification() {
        val n = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Alex").setContentText("1 new message").build()
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(Bundle().apply { putInt("text", 5); putLong("time", 1_000) }))
        val item = normalise("com.example.chat", n)!!.items.single()
        assertEquals("Alex", item.sender)
        assertEquals("1 new message", item.text)
    }

    // Review Focus 5
    @Test fun workProfileNotificationsBelongToTheWorkSource() {
        val personal = normalise("com.whatsapp", whatsAppChat())!!
        val workChat = normalise("com.whatsapp", whatsAppChat(), user = work)!!
        assertEquals(SourceId("app:com.whatsapp@10"), workChat.sourceId)
        assertNotEquals(personal.items.first().key, workChat.items.first().key)
    }
}
