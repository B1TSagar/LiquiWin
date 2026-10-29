package io.github.b1tsagar.liquiwin.feature.notifications

import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationDao
import io.github.b1tsagar.liquiwin.core.data.db.cache.NotificationItemEntity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Writes notifications into the store, one event at a time and in the order they came. The listener's callbacks
 * arrive on the main thread, so each method only queues its event.
 */
class NotificationIntake(
    private val normalise: (StatusBarNotification) -> Normalised?,
    private val dao: NotificationDao,
    private val actions: LiveActionCache,
    private val images: ImageStore,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (event in events) event() }
    }

    fun posted(sbn: StatusBarNotification) {
        events.trySend { guarded("a notification from ${sbn.packageName}") { store(sbn) } }
    }

    fun removed(notificationKey: String) {
        events.trySend {
            guarded("a removal") {
                dao.markRemoved(notificationKey, now())
                actions.remove(notificationKey)
            }
        }
    }

    /** What's showing after the listener connects: missed posts are stored and missed removals are marked. */
    fun synced(active: List<StatusBarNotification>) {
        events.trySend {
            for (sbn in active) guarded("a notification from ${sbn.packageName}") { store(sbn) }
            guarded("a sync") {
                val keys = active.mapTo(HashSet()) { it.key }
                dao.markRemovedExcept(keys, now())
                actions.retainOnly(keys)
            }
        }
    }

    fun cleanUp() {
        events.trySend {
            guarded("a clean-up") {
                images.delete(dao.cleanUp(now() - RETENTION_MS, PER_SOURCE))
                // Files no item names were left by a write that failed, or by a database the fallback rebuilt.
                val named = dao.allFiles().toSet()
                images.delete(images.dir.list().orEmpty().filterNot { it in named })
            }
        }
    }

    private suspend fun store(sbn: StatusBarNotification) {
        val normalised = normalise(sbn) ?: return
        val time = now()
        // An hour ahead allows for clocks that disagree; further ahead, an item would stay the newest for good, and
        // reading it would mark every later message read.
        val items = normalised.items.filter { it.postedAt in time - RETENTION_MS..time + CLOCK_SLACK_MS }.distinctBy { it.key }
        if (items.isEmpty()) return
        // Before the items, so a stored item never shows without its actions.
        actions.put(normalised.notificationKey, LiveActions(normalised.contentIntent, normalised.replyAction, normalised.buttons))
        val stored = dao.existingKeys(items.map { it.key }).toSet()
        val entities = items.map { item ->
            val isNew = item.key !in stored // a stored item keeps the images it has
            val imageFile = if (isNew) item.image?.let { images.save(it, ImageStore.IMAGE_PX) } else null
            NotificationItemEntity(
                key = item.key,
                notificationKey = normalised.notificationKey,
                sourceId = normalised.sourceId.value,
                conversationKey = item.conversationKey,
                conversationTitle = item.conversationTitle,
                shortcutId = item.shortcutId,
                sender = item.sender,
                text = item.text,
                avatarFile = if (isNew) item.avatar?.let { images.save(it, ImageStore.AVATAR_PX) } else null,
                imageFile = imageFile,
                postedAt = item.postedAt,
                removedAt = null,
                isSelf = item.isSelf,
                // Most chat apps let only Android's own panel read a photo, so the chat window shows a card (§5.7).
                photoUnreadable = isNew && item.image != null && imageFile == null,
            )
        }
        images.delete(dao.replaceNotification(normalised.notificationKey, entities))
    }

    // One bad notification never stops the rest. Logs never carry a title, text or image.
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NotificationIntake", "skipped $what", e)
        }
    }

    companion object {
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
        const val CLOCK_SLACK_MS = 60 * 60 * 1000L
        const val PER_SOURCE = 50
    }
}
