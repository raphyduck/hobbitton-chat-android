package com.librechat.android.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("parentMessageId"),
        Index(value = ["conversationId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val conversationId: String,
    val parentMessageId: String?,
    val sender: String?,
    val text: String?,
    val content: String?,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val iconURL: String?,
    val unfinished: Boolean = false,
    val error: Boolean = false,
    val finishReason: String?,
    val tokenCount: Int?,
    val feedback: String?,
    val files: String?,
    val attachments: String?,
    val metadata: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
