package com.garfiec.librechat.feature.conversations.export

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ConversationExport
import kotlinx.serialization.json.Json

class ConversationImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseJson(jsonString: String): Result<ConversationExport> {
        return try {
            val export = json.decodeFromString(ConversationExport.serializer(), jsonString)
            if (export.conversation.conversationId == null) {
                return Result.Error(message = "Invalid export: missing conversation ID")
            }
            if (export.messages.isEmpty()) {
                return Result.Error(message = "Invalid export: no messages found")
            }
            Result.Success(export)
        } catch (e: Exception) {
            Result.Error(e, "Failed to parse conversation file: ${e.message}")
        }
    }
}
