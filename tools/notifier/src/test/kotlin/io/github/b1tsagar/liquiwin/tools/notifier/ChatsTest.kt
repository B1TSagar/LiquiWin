package io.github.b1tsagar.liquiwin.tools.notifier

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun showing(id: Int) = manager.activeNotifications.single { it.id == id }.notification
    private fun style(id: Int) = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(showing(id))!!
    private fun texts(id: Int) = style(id).messages.map { it.text.toString() }

    @Test fun aReceivedMessageIsPostedAsAConversationWithAReplyAction() {
        Chats.receive(context, "alex", "see you at 6")
        assertEquals("alex", showing(1).shortcutId)
        assertEquals(listOf("see you at 6"), texts(1))
        assertEquals("Alex", style(1).messages.single().person?.name)
        val reply = showing(1).actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
        assertEquals("reply", reply.remoteInputs.single().resultKey)
    }

    @Test fun aGroupMessageNamesTheGroup() {
        Chats.receive(context, "family", "dinner at 7")
        assertTrue(style(2).isGroupConversation)
        assertEquals("Family", style(2).conversationTitle)
    }

    @Test fun aReplyAddsYourMessageThenTheEcho() {
        Chats.receive(context, "alex", "see you at 6")
        Chats.reply(context, "alex", "on my way")
        assertEquals(listOf("see you at 6", "on my way", "you said: on my way"), texts(1))
        assertNull(style(1).messages[1].person) // yours
    }

    @Test fun chatsKeepTheirLastFiveMessages() {
        repeat(7) { Chats.receive(context, "alex", "message $it") }
        assertEquals((2..6).map { "message $it" }, texts(1))
    }

    @Test fun onlyTheShellCanTriggerPosts() {
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, PostReceiver::class.java), 0)
        assertEquals("android.permission.DUMP", receiver.permission)
    }

    @Test fun aMissedCallHasTwoButtonsThatOnlyLog() {
        Chats.postCall(context)
        val call = showing(5)
        assertEquals(NotificationCompat.CATEGORY_MISSED_CALL, call.category)
        assertEquals(listOf("Call back", "Message"), call.actions.map { it.title.toString() })
        assertTrue(call.actions.all { it.remoteInputs.isNullOrEmpty() })
    }

    @Test fun aChatPhotoIsServedByTheNotifiersOwnProvider() {
        Chats.postPicture(context)
        val photo = style(1).messages.last()
        assertEquals("image/png", photo.dataMimeType)
        assertEquals("${context.packageName}.photos", photo.dataUri?.authority)
    }
}
