package io.github.b1tsagar.liquiwin.tools.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import java.io.File

/** Posts WhatsApp-like and Instagram-like notifications (spec §10). Keeps no state: each post starts from the one showing. */
object Chats {
    internal const val CHAT = "chat"
    internal const val REPLY = "reply"
    internal const val BUTTON = "button"
    private const val CHANNEL = "chats"
    private val ids = mapOf("alex" to 1, "family" to 2, "photo" to 3, "snap" to 4, "call" to 5)
    private val you = Person.Builder().setName("You").build()

    // The first person sends what you receive; a chat with more than one is a group.
    private class Conversation(val title: String, val people: List<Person>)
    private val conversations = mapOf(
        "alex" to Conversation("Alex", listOf(person("alex", "Alex", 0xFF1BA1E2.toInt()))),
        "family" to Conversation("Family", listOf(person("mum", "Mum", 0xFFD80073.toInt()), person("dad", "Dad", 0xFF60A917.toInt()))),
    )

    /** A message from [chat], "alex" or "family". */
    fun receive(context: Context, chat: String, text: String) {
        val conversation = conversations.getValue(chat)
        add(context, chat, conversation, message(text, conversation.people.first()))
    }

    /** Your message in [chat], then the echo "you said: <text>". */
    fun reply(context: Context, chat: String, text: String) {
        val conversation = conversations.getValue(chat)
        add(context, chat, conversation, message(text, null), message("you said: $text", conversation.people.first()))
    }

    /** Instagram-like: a big picture. */
    fun postPhoto(context: Context) = show(context, "photo") {
        setContentTitle("Instagram")
        setContentText("alex liked your photo")
        setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture(1080, 720)))
    }

    /** Snapchat-like: plain, no actions. */
    fun postSnap(context: Context) = show(context, "snap") {
        setContentTitle("Alex")
        setContentText("sent you a Snap")
    }

    /** A dialer-like missed call from Alex, whose **Call back** and **Message** only log (§10). */
    fun postCall(context: Context) = show(context, "call") {
        setContentTitle("Alex")
        setContentText("Missed call")
        setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
        for ((i, label) in listOf("Call back", "Message").withIndex()) {
            val press = Intent(context, ButtonReceiver::class.java).putExtra(BUTTON, label)
            addAction(0, label, PendingIntent.getBroadcast(context, i, press, PendingIntent.FLAG_IMMUTABLE))
        }
    }

    /** A photo from Alex in the alex chat, served by the notifier's own provider and granted to no app (§10). */
    fun postPicture(context: Context) {
        val file = File(context.cacheDir, "photos/picture.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { picture(720, 480).compress(Bitmap.CompressFormat.PNG, 100, it) }
        // FileProvider's form: content://<authority>/<root name in photos.xml>/<file>. shortcut: built by hand, since
        // getUriForFile compares paths with '/' and so throws under Robolectric on Windows; use it once tests run elsewhere.
        val uri = Uri.parse("content://${context.packageName}.photos/photos/${file.name}")
        val conversation = conversations.getValue("alex")
        add(context, "alex", conversation, message("a photo", conversation.people.first()).setData("image/png", uri))
    }

    /** What adb and the buttons post: [chat] is "alex", "family", "photo", "snap", "call" or "picture"; the last four ignore [text]. */
    internal fun post(context: Context, chat: String, text: String) {
        when (chat) {
            "alex", "family" -> receive(context, chat, text)
            "photo" -> postPhoto(context)
            "snap" -> postSnap(context)
            "call" -> postCall(context)
            "picture" -> postPicture(context)
        }
    }

    /** Clears [chat]'s notification, as messaging apps do once you open the chat. */
    internal fun cancel(context: Context, chat: String) {
        context.getSystemService(NotificationManager::class.java).cancel(ids[chat] ?: return)
    }

    private fun message(text: String, from: Person?) = NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), from)

    private fun add(context: Context, chat: String, conversation: Conversation, vararg new: NotificationCompat.MessagingStyle.Message) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val showing = manager.activeNotifications.firstOrNull { it.id == ids.getValue(chat) }
        val old = showing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }?.messages.orEmpty()
        val group = conversation.people.size > 1
        val style = NotificationCompat.MessagingStyle(you).setGroupConversation(group).setConversationTitle(conversation.title.takeIf { group })
        (old + new).takeLast(5).forEach { style.addMessage(it) }
        val shortcut = ShortcutInfoCompat.Builder(context, chat)
            .setShortLabel(conversation.title)
            .setLongLived(true)
            .setPersons(conversation.people.toTypedArray())
            .setIntent(Intent(context, NotifierActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(CHAT, chat))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        val replyIntent = Intent(context, ReplyReceiver::class.java).putExtra(CHAT, chat)
        val reply = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            PendingIntent.getBroadcast(context, ids.getValue(chat), replyIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
            .addRemoteInput(RemoteInput.Builder(REPLY).setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        show(context, chat) {
            setStyle(style)
            setShortcutId(chat)
            addAction(reply)
        }
    }

    // Tapping the notification opens the notifier on that chat.
    private fun show(context: Context, chat: String, build: NotificationCompat.Builder.() -> Unit) {
        val id = ids.getValue(chat)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "chats", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, NotifierActivity::class.java).putExtra(CHAT, chat)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .apply(build)
            .build()
        manager.notify(id, notification)
    }

    // Orange, with a white circle in the middle.
    private fun picture(width: Int, height: Int) = createBitmap(width, height).applyCanvas {
        drawColor(0xFFF09609.toInt())
        drawCircle(width / 2f, height / 2f, height / 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    // A 128 px circle in the person's own colour, with their initial.
    private fun person(key: String, name: String, color: Int): Person {
        val initial = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 64f; textAlign = Paint.Align.CENTER }
        val avatar = createBitmap(128, 128).applyCanvas {
            drawCircle(64f, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            drawText(name.take(1), 64f, 64f - (initial.descent() + initial.ascent()) / 2, initial)
        }
        return Person.Builder().setKey(key).setName(name).setIcon(IconCompat.createWithBitmap(avatar)).build()
    }
}
