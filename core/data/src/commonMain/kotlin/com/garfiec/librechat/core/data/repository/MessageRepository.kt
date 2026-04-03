package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun getMessages(conversationId: String): Result<List<Message>>
    suspend fun refreshMessages(conversationId: String): Result<List<Message>>
    suspend fun updateFeedback(conversationId: String, messageId: String, feedback: String?): Result<Unit>
    suspend fun updateMessageText(conversationId: String, messageId: String, text: String): Result<Unit>
    suspend fun branchMessage(conversationId: String, messageId: String, agentId: String? = null): Result<Message>
}
