package io.github.b1tsagar.liquiwin.feature.notifications

import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.db.cache.HiddenConversationEntity
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationDao
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationItemEntity
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.Chat
import io.github.b1tsagar.liquiwin.core.tiles.source.ChatMessage
import io.github.b1tsagar.liquiwin.core.tiles.source.ConversationRef
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileSource
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** All of one app's notifications in one profile, as a tile's source (§3.2). */
class AppNotificationSource(
    private val packageName: String,
    private val userSerial: Long,
    private val dao: NotificationDao,
    private val actions: LiveActionCache,
    private val imagesDir: File,
    private val catalog: AppCatalog,
    private val launcher: ItemLauncher,
) : TileSource {
    override val id = SourceId.forApp(packageName, userSerial)

    override val items: Flow<List<TileItem>> = combine(dao.observeItems(id.value), actions.actions) { stored, live ->
        stored.map { (item, unread) -> item.toItem(unread, live) }
    }

    override val unreadCount: Flow<Int> = items.map { items -> items.count { it.unread } }

    override val hiddenChats: Flow<List<HiddenChat>> = dao.observeHidden(id.value).map { hidden ->
        hidden.map { HiddenChat(id, it.conversationKey, it.name) }
    }

    override suspend fun open(item: TileItem?) {
        if (item != null) {
            markRead(listOf(item))
            val intent = dao.item(item.key)?.notificationKey?.let { actions.actions.value[it]?.contentIntent }
            if (intent != null && launcher.send(intent)) return
            val shortcutId = item.conversation?.shortcutId
            if (shortcutId != null && launcher.startShortcut(packageName, shortcutId, userSerial)) return
        }
        val app = app()
        if (app != null) catalog.launch(app.key) else launcher.appGone()
    }

    override suspend fun markRead(items: List<TileItem>) {
        for ((conversation, read) in items.groupBy { it.conversation?.key }) {
            if (conversation != null) dao.markRead(id.value, conversation, read.maxOf { it.time.toEpochMilli() })
        }
    }

    override fun chat(conversationKey: String): Flow<Chat?> =
        combine(dao.observeConversation(id.value, conversationKey), actions.actions) { stored, live ->
            if (stored.isEmpty()) return@combine null
            val group = stored.lastOrNull { it.item.conversationTitle != null }?.item?.conversationTitle
            val incoming = stored.lastOrNull { !it.item.isSelf }?.item
            Chat(
                conversationKey = conversationKey,
                title = group ?: incoming?.sender ?: app()?.label ?: packageName,
                avatar = if (group == null) incoming?.avatarFile?.let { ImageRef(File(imagesDir, it)) } else null,
                isGroup = group != null,
                messages = stored.map { (item, unread) -> ChatMessage(item.toItem(unread, live), item.sender, item.isSelf) },
                // A plain notification is keyed by itself, and an app may fill its slot with unrelated posts, so only messages
                // can be hidden. A one-to-one chat with no shortcut looks the same until it holds a message of yours.
                canHide = group != null || stored.any { (item) -> item.conversationKey != item.notificationKey || item.isSelf },
            )
        }

    override suspend fun reply(item: TileItem, text: String): ReplyResult {
        val notificationKey = dao.item(item.key)?.takeIf { it.removedAt == null }?.notificationKey
        val action = notificationKey?.let { actions.actions.value[it]?.reply }
        return if (action != null && launcher.reply(action, text)) ReplyResult.Sent else ReplyResult.ActionGone
    }

    override suspend fun hide(chat: Chat) {
        dao.hide(HiddenConversationEntity(id.value, chat.conversationKey, chat.title, System.currentTimeMillis()))
    }

    override suspend fun showAgain(conversationKey: String) {
        dao.showAgain(id.value, conversationKey)
    }

    private fun app() = catalog.apps.value.firstOrNull { it.key.packageName == packageName && it.key.userSerial == userSerial }

    private fun NotificationItemEntity.toItem(unread: Boolean, live: Map<String, LiveActions>) = TileItem(
        key = key,
        sourceId = id,
        title = if (conversationTitle != null) "$sender · $conversationTitle" else sender,
        text = text,
        avatar = avatarFile?.let { ImageRef(File(imagesDir, it)) },
        image = imageFile?.let { ImageRef(File(imagesDir, it)) },
        time = Instant.ofEpochMilli(postedAt),
        unread = unread,
        canReply = removedAt == null && live[notificationKey]?.reply != null,
        conversation = ConversationRef(conversationKey, shortcutId),
    )
}
