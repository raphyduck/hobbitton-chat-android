package com.librechat.android.core.data.mapper

import com.librechat.android.core.data.db.entity.ConversationEntity
import com.librechat.android.core.model.Conversation
import com.librechat.android.core.model.EModelEndpoint
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    conversationId = conversationId ?: "",
    title = title ?: "New Chat",
    user = user ?: "",
    endpoint = endpoint?.name,
    endpointType = endpointType?.name,
    model = model,
    agentId = agentId,
    isArchived = isArchived,
    tags = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), tags),
    iconURL = iconURL,
    greeting = greeting,
    modelParams = null,
    createdAt = parseTimestamp(createdAt),
    updatedAt = parseTimestamp(updatedAt),
)

fun ConversationEntity.toModel(): Conversation = Conversation(
    conversationId = conversationId,
    title = title,
    user = user,
    endpoint = endpoint?.let { name ->
        EModelEndpoint.entries.find { it.name == name }
    },
    endpointType = endpointType?.let { name ->
        EModelEndpoint.entries.find { it.name == name }
    },
    model = model,
    agentId = agentId,
    isArchived = isArchived,
    tags = try {
        json.decodeFromString<List<String>>(tags)
    } catch (_: Exception) {
        emptyList()
    },
    iconURL = iconURL,
    greeting = greeting,
    createdAt = formatTimestamp(createdAt),
    updatedAt = formatTimestamp(updatedAt),
)

fun List<ConversationEntity>.toModels(): List<Conversation> = map { it.toModel() }

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
