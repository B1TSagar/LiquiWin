package io.github.b1tsagar.liquiwin.core.data.db.cache

import androidx.room.ColumnInfo
import androidx.room.Entity

/** A chat or group whose messages stay off tiles until it's shown again. Its messages are kept. */
@Entity(tableName = "hidden_conversation", primaryKeys = ["source_id", "conversation_key"])
data class HiddenConversationEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    val name: String, // the chat's title when hidden, for the list in edit mode
    @ColumnInfo(name = "hidden_at") val hiddenAt: Long,
)
