package io.github.b1tsagar.liquiwin.feature.notifications

import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationDao
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.ConversationRef
import io.github.b1tsagar.liquiwin.core.tiles.source.ImageRef
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
        stored.map { (item, unread) ->
            TileItem(
                key = item.key,
                sourceId = id,
                title = if (item.conversationTitle != null) "${item.sender} · ${item.conversationTitle}" else item.sender,
                text = item.text,
                avatar = item.avatarFile?.let { ImageRef(File(imagesDir, it)) },
                image = item.imageFile?.let { ImageRef(File(imagesDir, it)) },
                time = Instant.ofEpochMilli(item.postedAt),
                unread = unread,
                canReply = item.removedAt == null && live[item.notificationKey]?.reply != null,
                conversation = ConversationRef(item.conversationKey, item.shortcutId),
            )
        }
    }

    override val unreadCount: Flow<Int> = items.map { items -> items.count { it.unread } }

    override suspend fun open(item: TileItem?) {
        if (item != null) {
            markRead(listOf(item))
            val intent = dao.item(item.key)?.notificationKey?.let { actions.actions.value[it]?.contentIntent }
            if (intent != null && launcher.send(intent)) return
            val shortcutId = item.conversation?.shortcutId
            if (shortcutId != null && launcher.startShortcut(packageName, shortcutId, userSerial)) return
        }
        val app = catalog.apps.value.firstOrNull { it.key.packageName == packageName && it.key.userSerial == userSerial }
        if (app != null) catalog.launch(app.key) else launcher.appGone()
    }

    override suspend fun markRead(items: List<TileItem>) {
        for ((conversation, read) in items.groupBy { it.conversation?.key }) {
            if (conversation != null) dao.markRead(id.value, conversation, read.maxOf { it.time.toEpochMilli() })
        }
    }
}
