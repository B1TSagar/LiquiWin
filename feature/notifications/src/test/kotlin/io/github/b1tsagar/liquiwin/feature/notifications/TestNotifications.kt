package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider

// Notifications in each app's shape, with made-up names and texts.

internal val context: Context get() = ApplicationProvider.getApplicationContext()

// The deprecated constructor is the public one; uid 10_123 is any app's.
@Suppress("DEPRECATION")
internal fun sbn(pkg: String, notification: Notification, id: Int = 1, tag: String? = null, postTime: Long = 5_000, user: UserHandle = Process.myUserHandle()) =
    StatusBarNotification(pkg, pkg, id, tag, 10_123, 0, 0, notification, user, postTime)

private fun intent(action: String) = PendingIntent.getActivity(context, 0, Intent(action), PendingIntent.FLAG_IMMUTABLE)

private fun builder() = NotificationCompat.Builder(context, "c")
    .setSmallIcon(android.R.drawable.sym_action_chat)
    .setContentIntent(intent("open"))

private fun action(title: String, semantic: Int = Action.SEMANTIC_ACTION_NONE, input: RemoteInput? = null) =
    Action.Builder(null, title, intent(title)).setSemanticAction(semantic).apply { if (input != null) addRemoteInput(input) }.build()

private fun person(name: String) = Person.Builder().setName(name).build()
private val you get() = person("You")
private val alex get() = Person.Builder().setName("Alex").setKey("alex")
    .setIcon(IconCompat.createWithBitmap(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)))
    .build()

internal fun whatsAppChat(
    messages: List<Pair<String, Long>> = listOf("are you coming?" to 1_000L, "see you at 6" to 2_000L),
    mine: List<Pair<String, Long>> = emptyList(),
): Notification {
    val style = MessagingStyle(you)
    for ((text, at) in messages) style.addMessage(text, at, alex)
    for ((text, at) in mine) style.addMessage(text, at, null as Person?)
    return builder().setStyle(style).setShortcutId("alex")
        .addAction(action("Mark as read", Action.SEMANTIC_ACTION_MARK_AS_READ))
        .addAction(action("Reply", Action.SEMANTIC_ACTION_REPLY, RemoteInput.Builder("reply").setLabel("Reply").build()))
        .build()
}

internal fun whatsAppGroup(): Notification = builder()
    .setStyle(
        MessagingStyle(you).setConversationTitle("Family").setGroupConversation(true)
            .addMessage("on my way", 1_000, person("Mum"))
            .addMessage("bring bread", 2_000, person("Dad")),
    )
    .setShortcutId("family")
    .build()

internal fun whatsAppSummary(): Notification =
    builder().setContentTitle("WhatsApp").setContentText("2 new messages").setGroup("whatsapp").setGroupSummary(true).build()

internal fun gmailSummary(): Notification =
    builder().setContentTitle("Gmail").setContentText("3 new emails").setGroup("gmail").setGroupSummary(true).build()

internal fun instagramLike(): Notification = builder().setContentTitle("Instagram").setContentText("sam_k liked your photo").setWhen(3_000)
    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)))
    .build()

internal fun snapchatSnap(): Notification = builder().setContentTitle("Alex").setContentText("sent you a Snap").setWhen(0).build()

internal fun messengerChat(): Notification = builder()
    .setStyle(MessagingStyle(you).addMessage("you up?", 1_000, person("Jo")))
    .addAction(action("Reply", input = RemoteInput.Builder("reply").setAllowFreeFormInput(true).build()))
    .build()

internal fun telegramPhoto(): Notification = builder()
    .setStyle(
        MessagingStyle(you).addMessage(
            MessagingStyle.Message("photo", 1_000, alex).setData("image/jpeg", Uri.parse("content://org.telegram.messenger.provider/media/1")),
        ),
    )
    .build()

/** A dialer's missed call from Alex, with [labels] as its buttons, then [replyLabel]'s button that takes a reply. */
internal fun missedCall(labels: List<String> = listOf("Call back", "Message"), replyLabel: String? = null): Notification = builder()
    .setContentTitle("Alex").setContentText("Missed call").setCategory(NotificationCompat.CATEGORY_MISSED_CALL).setWhen(3_000)
    .apply { for (label in labels) addAction(action(label)) }
    .apply { if (replyLabel != null) addAction(action(replyLabel, Action.SEMANTIC_ACTION_REPLY, RemoteInput.Builder("reply").build())) }
    .build()

internal fun gmailEmail(): Notification = builder().setContentTitle("Pat Lee").setContentText("Lunch?").setWhen(4_000)
    .setStyle(NotificationCompat.BigTextStyle().bigText("Lunch on Friday? The new place on King St."))
    .addAction(action("Archive"))
    .addAction(action("Reply"))
    .build()

internal fun navigation(): Notification = builder().setContentTitle("Head north").setContentText("on King St").setOngoing(true).build()

// androidx's MediaStyle is in androidx.media, which LiquiWin doesn't have.
internal fun musicPlayer(): Notification = Notification.Builder(context, "c").setSmallIcon(android.R.drawable.sym_action_chat)
    .setContentTitle("A song").setContentText("An artist").setStyle(Notification.MediaStyle()).build()

internal fun download(): Notification =
    builder().setContentTitle("photo.jpg").setContentText("Downloading").setProgress(100, 40, false).build()

internal fun ownNotification(): Notification = builder().setContentTitle("Live tiles are paused").setContentText("tap to fix").build()
