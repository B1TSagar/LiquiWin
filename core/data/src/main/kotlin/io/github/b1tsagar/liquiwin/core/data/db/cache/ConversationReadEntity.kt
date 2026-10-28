package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.ColumnInfo
import androidx.room.Entity

/** How far a conversation has been read. Its items up to [lastReadAt] are read. */
@Entity(tableName = "conversation_read", primaryKeys = ["source_id", "conversation_key"])
data class ConversationReadEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long,
)
