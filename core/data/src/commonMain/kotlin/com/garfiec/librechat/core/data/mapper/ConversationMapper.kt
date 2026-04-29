package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EModelEndpoint
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

// Legacy compat: pre-fix rows stored the Kotlin enum `.name` (e.g. "OPENAI").
// Remove once a Room schema migration normalizes the column to wire format.
private fun normalizeEndpoint(stored: String?): String? {
    if (stored == null) return null
    if (stored in EModelEndpoint.BUILT_IN_NAMES) return stored
    runCatching { EModelEndpoint.valueOf(stored).toSerialName() }
        .getOrNull()?.let { return it }
    return stored
}

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    conversationId = conversationId ?: "",
    title = title ?: "New Chat",
    user = user ?: "",
    endpoint = endpoint,
    endpointType = endpointType,
    model = model,
    agentId = agentId,
    isArchived = isArchived,
    tags = json.encodeToString(ListSerializer(serializer<String>()), tags),
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
    endpoint = normalizeEndpoint(endpoint),
    endpointType = normalizeEndpoint(endpointType),
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
    if (dateString == null) return Clock.System.now().toEpochMilliseconds()
    return try {
        Instant.parse(dateString).toEpochMilliseconds()
    } catch (_: Exception) {
        Clock.System.now().toEpochMilliseconds()
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    return Instant.fromEpochMilliseconds(epochMillis).toString()
}
