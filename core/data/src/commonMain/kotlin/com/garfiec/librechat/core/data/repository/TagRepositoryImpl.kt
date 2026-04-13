package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModels
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.network.api.TagsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagsApi: TagsApi,
    private val tagDao: ConversationTagDao,
    private val conversationRepository: ConversationRepository,
) : TagRepository {

    override fun observeTags(): Flow<List<ConversationTag>> =
        tagDao.getAllTags().map { it.toModels() }

    override suspend fun refreshTags(): Result<Unit> = safeApiCall {
        val tags = tagsApi.getTags()
        tagDao.replaceAll(tags.map { it.toEntity() })
    }

    override suspend fun setConversationTags(
        conversationId: String,
        tags: List<String>,
    ): Result<Unit> = safeApiCall {
        tagsApi.updateConversationTags(conversationId, tags)
        conversationRepository.updateConversationTagsLocal(conversationId, tags)
    }

    override suspend fun toggleFavorite(
        conversationId: String,
        currentTags: List<String>,
    ): Result<Unit> {
        val newTags = if (SAVED_TAG in currentTags) {
            currentTags.filterNot { it == SAVED_TAG }
        } else {
            currentTags + SAVED_TAG
        }
        return setConversationTags(conversationId, newTags)
    }

    override suspend fun clearCache() {
        tagDao.deleteAll()
    }
}
