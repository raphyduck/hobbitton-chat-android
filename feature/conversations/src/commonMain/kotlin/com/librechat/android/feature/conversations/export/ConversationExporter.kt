package com.librechat.android.feature.conversations.export

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.MessageRepository
import com.librechat.android.core.model.ConversationExport
import com.librechat.android.core.model.Message
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class ConversationExporter(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportAsJson(conversationId: String): Result<String> {
        val conversation = when (val result = conversationRepository.getConversation(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        val messages = when (val result = messageRepository.getMessages(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        val export = ConversationExport(
            conversation = conversation,
            messages = messages,
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            version = 1,
        )
        return Result.Success(json.encodeToString(ConversationExport.serializer(), export))
    }

    suspend fun exportAsMarkdown(conversationId: String): Result<String> {
        val conversation = when (val result = conversationRepository.getConversation(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        val messages = when (val result = messageRepository.getMessages(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val exportDate = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')} ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"

        val sb = StringBuilder()
        sb.appendLine("# ${conversation.title ?: "Untitled Conversation"}")
        sb.appendLine("Exported on $exportDate")
        sb.appendLine()

        for (message in messages) {
            val role = if (message.isCreatedByUser) "User" else "Assistant"
            sb.appendLine("## $role")
            sb.appendLine(extractMessageText(message))
            sb.appendLine()
        }

        return Result.Success(sb.toString().trimEnd())
    }

    private fun extractMessageText(message: Message): String {
        // Prefer content parts if available, otherwise fall back to text
        val parts = message.content
        if (!parts.isNullOrEmpty()) {
            return parts.mapNotNull { part ->
                part.text ?: part.think?.let { "[Thinking] $it" }
            }.joinToString("\n")
        }
        return message.text
    }
}
