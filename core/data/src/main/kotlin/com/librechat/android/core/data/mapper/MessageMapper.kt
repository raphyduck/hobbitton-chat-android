package com.librechat.android.core.data.mapper

import com.librechat.android.core.data.db.entity.MessageEntity
import com.librechat.android.core.model.Attachment
import com.librechat.android.core.model.Feedback
import com.librechat.android.core.model.FileReference
import com.librechat.android.core.model.Message
import com.librechat.android.core.model.MessageContentPart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val json = Json { ignoreUnknownKeys = true }

fun Message.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId,
    conversationId = conversationId,
    parentMessageId = parentMessageId,
    sender = sender,
    text = text,
    content = content?.let { json.encodeToString(ListSerializer(MessageContentPart.serializer()), it) },
    isCreatedByUser = isCreatedByUser,
    model = model,
    endpoint = endpoint,
    iconURL = iconURL,
    unfinished = unfinished,
    error = error,
    finishReason = finishReason,
    tokenCount = tokenCount,
    feedback = feedback?.let { json.encodeToString(Feedback.serializer(), it) },
    files = files?.let { json.encodeToString(ListSerializer(FileReference.serializer()), it) },
    attachments = attachments?.let { json.encodeToString(ListSerializer(Attachment.serializer()), it) },
    metadata = metadata?.toString(),
    createdAt = parseTimestamp(createdAt),
    updatedAt = parseTimestamp(updatedAt),
)

fun MessageEntity.toModel(): Message = Message(
    messageId = messageId,
    conversationId = conversationId,
    parentMessageId = parentMessageId,
    sender = sender,
    text = text ?: "",
    content = content?.let {
        try { json.decodeFromString<List<MessageContentPart>>(it) } catch (_: Exception) { null }
    },
    isCreatedByUser = isCreatedByUser,
    model = model,
    endpoint = endpoint,
    iconURL = iconURL,
    unfinished = unfinished,
    error = error,
    finishReason = finishReason,
    tokenCount = tokenCount,
    feedback = feedback?.let {
        try { json.decodeFromString<Feedback>(it) } catch (_: Exception) { null }
    },
    files = files?.let {
        try { json.decodeFromString<List<FileReference>>(it) } catch (_: Exception) { null }
    },
    attachments = attachments?.let {
        try { json.decodeFromString<List<Attachment>>(it) } catch (_: Exception) { null }
    },
    metadata = metadata?.let {
        try { json.decodeFromString<JsonObject>(it) } catch (_: Exception) { null }
    },
    createdAt = formatTimestamp(createdAt),
    updatedAt = formatTimestamp(updatedAt),
)

fun List<MessageEntity>.toModels(): List<Message> = map { it.toModel() }

private fun parseTimestamp(dateString: String?): Long {
    if (dateString == null) return System.currentTimeMillis()
    return try {
        java.time.Instant.parse(dateString).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    return java.time.Instant.ofEpochMilli(epochMillis).toString()
}
