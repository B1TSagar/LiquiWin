package io.github.b1tsagar.liquiwin.feature.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.BundleCompat
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId

data class NormalisedItem(
    val key: String, // §5.2's dedupe key
    val conversationKey: String,
    val conversationTitle: String?, // groups only
    val shortcutId: String?,
    val sender: String,
    val text: String?,
    val postedAt: Long,
    val isSelf: Boolean,
    val avatar: IconCompat?,
    val image: IconCompat?,
)

data class Normalised(
    val notificationKey: String, // StatusBarNotification.key
    val sourceId: SourceId,
    val items: List<NormalisedItem>, // never empty
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
    val buttons: List<Notification.Action> = emptyList(), // a missed call's, up to two, in the app's order (§5.1)
)

/** Turns another app's notification into the items its tile shows (§5.2). */
class Normaliser(private val context: Context, private val serialOf: (UserHandle) -> Long) {
    /** Null for a notification LiquiWin ignores (§5.2). */
    fun normalise(sbn: StatusBarNotification): Normalised? {
        if (sbn.packageName == context.packageName || isIgnored(sbn)) return null
        val notification = sbn.notification
        val sourceId = SourceId.forApp(sbn.packageName, serialOf(sbn.user))
        val shortcutId = NotificationCompat.getShortcutId(notification)
        val items = messages(sbn, sourceId, shortcutId) ?: plain(sbn, sourceId, shortcutId) ?: return null
        return Normalised(sbn.key, sourceId, items, notification.contentIntent, replyAction(notification), buttons(notification))
    }

    private fun messages(sbn: StatusBarNotification, sourceId: SourceId, shortcutId: String?): List<NormalisedItem>? {
        try {
            val notification = sbn.notification
            val style = MessagingStyle.extractMessagingStyleFromNotification(notification) ?: return null
            val user = style.user
            val title = if (style.isGroupConversation) style.conversationTitle else null
            val largeIcon by lazy { largeIcon(notification) }
            val items = style.messages.mapNotNull { message ->
                val image = message.dataUri?.takeIf { message.dataMimeType?.startsWith("image/") == true }
                    ?.let(IconCompat::createWithContentUri)
                if (message.text.isNullOrBlank() && image == null) return@mapNotNull null
                val person = message.person
                item(
                    sourceId, shortcutId, conversationKey = shortcutId ?: sbn.key, conversationTitle = title,
                    sender = if (person == null) user.name else person.name, text = message.text, postedAt = message.timestamp,
                    isSelf = person == null || isUser(person, user), avatar = person?.icon ?: largeIcon, image = image,
                )
            }
            return items.ifEmpty { null }
        } catch (e: RuntimeException) {
            return null // another app's extras can hold anything, so a style that can't be read is read as plain
        }
    }

    private fun plain(sbn: StatusBarNotification, sourceId: SourceId, shortcutId: String?): List<NormalisedItem>? {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        return listOf(
            item(
                sourceId, shortcutId, conversationKey = sbn.key, conversationTitle = null,
                sender = title, text = text, postedAt = notification.`when`.takeIf { it != 0L } ?: sbn.postTime,
                isSelf = false, avatar = largeIcon(notification), image = picture(extras),
            ),
        )
    }

    private fun picture(extras: Bundle): IconCompat? {
        BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)?.let { return IconCompat.createWithBitmap(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)?.let(::iconCompat)
    }

    private fun largeIcon(notification: Notification): IconCompat? = notification.getLargeIcon()?.let(::iconCompat)

    // A resource icon from a package LiquiWin can't read throws, and the item goes without that image.
    private fun iconCompat(icon: Icon): IconCompat? = try {
        IconCompat.createFromIcon(context, icon)
    } catch (e: RuntimeException) {
        null
    }
}

private const val SENDER_MAX = 200
private const val TEXT_MAX = 2_000
private const val PROGRESS_TEMPLATE = "android.app.Notification\$ProgressStyle"
private const val CATEGORY_MISSED_CALL = "missed_call" // Notification.CATEGORY_MISSED_CALL is API 30
private val MEDIA_TEMPLATES = setOf("android.app.Notification\$MediaStyle", "android.app.Notification\$DecoratedMediaCustomViewStyle")

// The ignore list is stage 6's.
private fun isIgnored(sbn: StatusBarNotification): Boolean {
    val notification = sbn.notification
    val extras = notification.extras
    val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
    return notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
        sbn.isOngoing ||
        template in MEDIA_TEMPLATES || extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
        template == PROGRESS_TEMPLATE || extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 ||
        extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
}

private fun item(
    sourceId: SourceId, shortcutId: String?, conversationKey: String, conversationTitle: CharSequence?,
    sender: CharSequence?, text: CharSequence?, postedAt: Long, isSelf: Boolean, avatar: IconCompat?, image: IconCompat?,
): NormalisedItem {
    val name = (sender ?: "").cut(SENDER_MAX)
    val body = text?.cut(TEXT_MAX)
    // A message seen again keeps its key, and an edit changes its text and so its key (§5.2).
    val key = listOf(sourceId.value, conversationKey, name, postedAt, (body ?: "").hashCode()).joinToString("|")
    return NormalisedItem(key, conversationKey, conversationTitle?.cut(SENDER_MAX), shortcutId, name, body, postedAt, isSelf, avatar, image)
}

// The same key when both have one, otherwise the same name.
private fun isUser(person: Person, user: Person): Boolean {
    val key = person.key
    val userKey = user.key
    if (key != null && userKey != null) return key == userKey
    val name = person.name?.toString()
    return name != null && name == user.name?.toString()
}

// The only action kept beside a missed call's buttons. LiquiWin never sends an app's mark-as-read, which would send a
// read receipt (§5.3).
private fun replyAction(notification: Notification): Notification.Action? {
    val actions = notification.actions ?: return null
    return actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY && !it.remoteInputs.isNullOrEmpty() }
        ?: actions.firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }
}

// A missed call's own buttons, such as Call back and Message, which do what they do in Android's panel (§5.1).
private fun buttons(notification: Notification): List<Notification.Action> {
    if (notification.category != CATEGORY_MISSED_CALL) return emptyList()
    return notification.actions.orEmpty().filter { !it.title.isNullOrBlank() && it.actionIntent != null }.take(2)
}

private fun CharSequence.cut(max: Int): String = toString().take(max) // plain text, without spans
