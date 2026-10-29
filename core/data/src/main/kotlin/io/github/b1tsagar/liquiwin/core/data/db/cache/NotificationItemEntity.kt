package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One message, or one plain notification, under the key that dedupes it across re-posts. */
@Entity(tableName = "notification_item", indices = [Index("source_id"), Index("notification_key")])
data class NotificationItemEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "conversation_title") val conversationTitle: String?,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String?,
    val sender: String,
    val text: String?,
    @ColumnInfo(name = "avatar_file") val avatarFile: String?,
    @ColumnInfo(name = "image_file") val imageFile: String?,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
    @ColumnInfo(name = "photo_unreadable", defaultValue = "0") val photoUnreadable: Boolean = false, // §5.7's card
)
