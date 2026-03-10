package com.librechat.android.feature.conversations.export

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.MessageRepository
import com.librechat.android.core.model.ConversationExport
import com.librechat.android.core.model.Message
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ConversationExporter @Inject constructor(
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
            exportedAt = System.currentTimeMillis(),
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

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val exportDate = dateFormat.format(Date())

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
