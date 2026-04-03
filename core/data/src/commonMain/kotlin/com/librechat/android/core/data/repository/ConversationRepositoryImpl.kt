package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.data.db.dao.ConversationDao
import com.librechat.android.core.data.mapper.toEntity
import com.librechat.android.core.data.mapper.toModel
import com.librechat.android.core.data.mapper.toModels
import com.librechat.android.core.model.Conversation
import com.librechat.android.core.model.request.ForkConversationRequest
import com.librechat.android.core.network.api.ConversationsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import co.touchlab.kermit.Logger

class ConversationRepositoryImpl(
    private val conversationsApi: ConversationsApi,
    private val conversationDao: ConversationDao,
) : ConversationRepository {

    override fun observeConversations(isArchived: Boolean): Flow<Result<List<Conversation>>> {
        return conversationDao.getAllConversations(
            isArchived = isArchived,
        ).map { entities ->
            Result.Success(entities.toModels()) as Result<List<Conversation>>
        }
    }

    override suspend fun loadNextPage(
        cursor: String?,
        tags: List<String>?,
        search: String?,
        sortBy: String?,
        sortDirection: String?,
        isArchived: Boolean,
    ): Result<String?> {
        return safeApiCall {
            val response = conversationsApi.getConversations(
                cursor = cursor,
                isArchived = isArchived,
                tags = tags,
                search = search,
                sortBy = sortBy,
                sortDirection = sortDirection,
            )
            // Cache conversations locally
            val entities = response.conversations.map { it.toEntity() }
            conversationDao.upsertAll(entities)
            response.nextCursor
        }
    }

    override suspend fun getConversation(id: String): Result<Conversation> {
        // Try local cache first
        val cached = conversationDao.getById(id)
        if (cached != null) return Result.Success(cached.toModel())

        // Fetch from network
        return safeApiCall {
            val conversation = conversationsApi.getConversation(id)
            conversationDao.upsert(conversation.toEntity())
            conversation
        }
    }

    override suspend fun updateTitle(id: String, title: String): Result<Conversation> {
        return safeApiCall {
            val updated = conversationsApi.updateTitle(id, title)
            conversationDao.updateTitle(id, title, Clock.System.now().toEpochMilliseconds())
            updated
        }
    }

    override suspend fun generateTitle(conversationId: String): Result<String> {
        return safeApiCall {
            val response = conversationsApi.generateTitle(conversationId)
            // Update the local cache with the generated title
            conversationDao.updateTitle(conversationId, response.title, Clock.System.now().toEpochMilliseconds())
            response.title
        }
    }

    override suspend fun archive(id: String, isArchived: Boolean): Result<Conversation> {
        return safeApiCall {
            val updated = conversationsApi.archive(id, isArchived)
            conversationDao.updateArchived(id, isArchived, Clock.System.now().toEpochMilliseconds())
            updated
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        return safeApiCall {
            conversationsApi.deleteConversation(id)
            conversationDao.deleteById(id)
        }
    }

    override suspend fun forkConversation(
        conversationId: String,
        messageId: String,
        option: String?,
        splitAtTarget: Boolean?,
        latestMessageId: String?,
    ): Result<Conversation> {
        return safeApiCall {
            val response = conversationsApi.forkConversation(
                ForkConversationRequest(
                    conversationId = conversationId,
                    messageId = messageId,
                    option = option,
                    splitAtTarget = splitAtTarget,
                    latestMessageId = latestMessageId,
                ),
            )
            val conversation = response.conversation
            conversation.conversationId?.let { conversationDao.upsert(conversation.toEntity()) }
            conversation
        }
    }

    override suspend fun duplicateConversation(
        conversationId: String,
        title: String?,
    ): Result<Conversation> {
        return safeApiCall {
            val duplicated = conversationsApi.duplicateConversation(conversationId, title)
            duplicated.conversationId?.let { conversationDao.upsert(duplicated.toEntity()) }
            duplicated
        }
    }

    override suspend fun importConversation(jsonContent: String): Result<Conversation> {
        return safeApiCall {
            val fileBytes = jsonContent.encodeToByteArray()
            val imported = conversationsApi.importConversations(
                fileBytes = fileBytes,
                filename = "conversation.json",
                contentType = "application/json",
            )
            imported.conversationId?.let { conversationDao.upsert(imported.toEntity()) }
            imported
        }
    }

    override suspend fun deleteAll(): Result<Unit> {
        return safeApiCall {
            conversationsApi.deleteAllConversations()
            conversationDao.deleteAll()
        }
    }

    override suspend fun saveConversation(conversation: Conversation) {
        val id = conversation.conversationId ?: return
        if (id.isBlank()) return
        conversationDao.upsert(conversation.toEntity())
    }

    suspend fun refreshConversations() {
        try {
            val response = conversationsApi.getConversations()
            val entities = response.conversations.map { it.toEntity() }
            conversationDao.upsertAll(entities)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to refresh conversations" }
        }
    }
}
