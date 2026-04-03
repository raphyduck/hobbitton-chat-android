package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(isArchived: Boolean = false): Flow<Result<List<Conversation>>>
    suspend fun loadNextPage(
        cursor: String?,
        tags: List<String>? = null,
        search: String? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        isArchived: Boolean = false,
    ): Result<String?>
    suspend fun getConversation(id: String): Result<Conversation>
    suspend fun updateTitle(id: String, title: String): Result<Conversation>
    suspend fun generateTitle(conversationId: String): Result<String>
    suspend fun archive(id: String, isArchived: Boolean): Result<Conversation>
    suspend fun delete(id: String): Result<Unit>
    suspend fun forkConversation(
        conversationId: String,
        messageId: String,
        option: String? = null,
        splitAtTarget: Boolean? = null,
        latestMessageId: String? = null,
    ): Result<Conversation>
    suspend fun duplicateConversation(conversationId: String, title: String?): Result<Conversation>
    suspend fun importConversation(jsonContent: String): Result<Conversation>
    suspend fun deleteAll(): Result<Unit>
    suspend fun saveConversation(conversation: Conversation)
}
