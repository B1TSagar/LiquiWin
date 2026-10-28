package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class StoredItem(@Embedded val item: NotificationItemEntity, val unread: Boolean)

/**
 * Stored notifications and their read state. An item is unread while it isn't yours and its conversation hasn't been
 * read up to it; removal reads the conversation instead of keeping a flag per item.
 */
@Dao
abstract class NotificationDao {
    /** Newest first, then by key, leaving out hidden chats. */
    @Query(
        """
        SELECT i.*, (i.is_self = 0 AND i.posted_at > COALESCE(r.last_read_at, 0)) AS unread
        FROM notification_item i
        LEFT JOIN conversation_read r ON r.source_id = i.source_id AND r.conversation_key = i.conversation_key
        WHERE i.source_id = :sourceId
            AND NOT EXISTS (SELECT 1 FROM hidden_conversation h WHERE h.source_id = i.source_id AND h.conversation_key = i.conversation_key)
        ORDER BY i.posted_at DESC, i.`key`
        """,
    )
    abstract fun observeItems(sourceId: String): Flow<List<StoredItem>>

    /** Oldest first, with your own, removed and hidden messages. */
    @Query(
        """
        SELECT i.*, (i.is_self = 0 AND i.posted_at > COALESCE(r.last_read_at, 0)) AS unread
        FROM notification_item i
        LEFT JOIN conversation_read r ON r.source_id = i.source_id AND r.conversation_key = i.conversation_key
        WHERE i.source_id = :sourceId AND i.conversation_key = :conversationKey
        ORDER BY i.posted_at, i.`key`
        """,
    )
    abstract fun observeConversation(sourceId: String, conversationKey: String): Flow<List<StoredItem>>

    /** Newest hidden first. */
    @Query("SELECT * FROM hidden_conversation WHERE source_id = :sourceId ORDER BY hidden_at DESC, conversation_key")
    abstract fun observeHidden(sourceId: String): Flow<List<HiddenConversationEntity>>

    /** Hiding a hidden chat again updates its name and time. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun hide(chat: HiddenConversationEntity)

    @Query("DELETE FROM hidden_conversation WHERE source_id = :sourceId AND conversation_key = :conversationKey")
    abstract suspend fun showAgain(sourceId: String, conversationKey: String)

    @Query("SELECT `key` FROM notification_item WHERE `key` IN (:keys)")
    abstract suspend fun existingKeys(keys: List<String>): List<String>

    @Query("SELECT * FROM notification_item WHERE `key` = :key")
    abstract suspend fun item(key: String): NotificationItemEntity?

    /** Stores a notification's items. Returns the image files of items its last post had and this one hasn't. */
    @Transaction
    open suspend fun replaceNotification(notificationKey: String, items: List<NotificationItemEntity>): List<String> {
        val keys = items.mapTo(HashSet()) { it.key }
        val gone = itemsOf(notificationKey).filter { it.key !in keys }
        delete(gone)
        val stored = existingKeys(items.map { it.key }).toSet()
        insert(items.filter { it.key !in stored })
        // A stored key already fixes the sender, text and time, so only where it's showing changes, and its images stay.
        for (item in items) if (item.key in stored) refresh(item.key, notificationKey, item.conversationTitle, item.shortcutId)
        return gone.files()
    }

    @Transaction
    open suspend fun markRemoved(notificationKey: String, now: Long) {
        val showing = showingItemsOf(notificationKey)
        if (showing.isEmpty()) return
        setRemoved(notificationKey, now)
        for ((conversation, items) in showing.groupBy { it.sourceId to it.conversationKey }) {
            markRead(conversation.first, conversation.second, items.maxOf { it.postedAt })
        }
    }

    // Filtered here rather than with NOT IN, which could pass API 29's limit of 999 variables.
    @Transaction
    open suspend fun markRemovedExcept(activeKeys: Set<String>, now: Long) {
        for (key in showingNotificationKeys()) if (key !in activeKeys) markRemoved(key, now)
    }

    /** A read mark never moves back. */
    @Query(
        """
        INSERT OR REPLACE INTO conversation_read (source_id, conversation_key, last_read_at)
        VALUES (:sourceId, :conversationKey, MAX(:upTo, COALESCE(
            (SELECT last_read_at FROM conversation_read WHERE source_id = :sourceId AND conversation_key = :conversationKey), 0)))
        """,
    )
    abstract suspend fun markRead(sourceId: String, conversationKey: String, upTo: Long)

    /**
     * Deletes items older than [cutoff], then all but the newest [perSource] of each source. Returns their image files.
     * Hidden chats stay hidden: they're kept until shown again.
     */
    @Transaction
    open suspend fun cleanUp(cutoff: Long, perSource: Int): List<String> {
        val deleted = itemsBefore(cutoff).toMutableList()
        deleteBefore(cutoff)
        for (source in sourceIds()) deleted += beyondNewest(source, perSource).also { delete(it) }
        deleteReadMarksBefore(cutoff)
        return deleted.files()
    }

    /** Every avatar and image file an item names. */
    @Query(
        """
        SELECT avatar_file FROM notification_item WHERE avatar_file IS NOT NULL
        UNION ALL SELECT image_file FROM notification_item WHERE image_file IS NOT NULL
        """,
    )
    abstract suspend fun allFiles(): List<String>

    @Query("SELECT * FROM notification_item WHERE notification_key = :notificationKey")
    protected abstract suspend fun itemsOf(notificationKey: String): List<NotificationItemEntity>

    @Query("SELECT * FROM notification_item WHERE notification_key = :notificationKey AND removed_at IS NULL")
    protected abstract suspend fun showingItemsOf(notificationKey: String): List<NotificationItemEntity>

    @Query("SELECT DISTINCT notification_key FROM notification_item WHERE removed_at IS NULL")
    protected abstract suspend fun showingNotificationKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE) // two messages of one post can share a key
    protected abstract suspend fun insert(items: List<NotificationItemEntity>)

    @Delete
    protected abstract suspend fun delete(items: List<NotificationItemEntity>)

    @Query(
        """
        UPDATE notification_item SET notification_key = :notificationKey, conversation_title = :conversationTitle,
            shortcut_id = :shortcutId, removed_at = NULL
        WHERE `key` = :key
        """,
    )
    protected abstract suspend fun refresh(key: String, notificationKey: String, conversationTitle: String?, shortcutId: String?)

    @Query("UPDATE notification_item SET removed_at = :now WHERE notification_key = :notificationKey AND removed_at IS NULL")
    protected abstract suspend fun setRemoved(notificationKey: String, now: Long)

    @Query("SELECT * FROM notification_item WHERE posted_at < :cutoff")
    protected abstract suspend fun itemsBefore(cutoff: Long): List<NotificationItemEntity>

    @Query("DELETE FROM notification_item WHERE posted_at < :cutoff")
    protected abstract suspend fun deleteBefore(cutoff: Long)

    @Query("SELECT DISTINCT source_id FROM notification_item")
    protected abstract suspend fun sourceIds(): List<String>

    @Query("SELECT * FROM notification_item WHERE source_id = :sourceId ORDER BY posted_at DESC, `key` LIMIT -1 OFFSET :keep")
    protected abstract suspend fun beyondNewest(sourceId: String, keep: Int): List<NotificationItemEntity>

    @Query("DELETE FROM conversation_read WHERE last_read_at < :cutoff")
    protected abstract suspend fun deleteReadMarksBefore(cutoff: Long)

    private fun List<NotificationItemEntity>.files() = flatMap { listOfNotNull(it.avatarFile, it.imageFile) }
}
