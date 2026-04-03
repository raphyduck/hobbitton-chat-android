package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.network.api.TagsApi

class TagRepositoryImpl(
    private val tagsApi: TagsApi,
) : TagRepository {

    override suspend fun getTags(): Result<List<ConversationTag>> =
        safeApiCall { tagsApi.getTags() }

    override suspend fun updateConversationTags(
        conversationId: String,
        tags: List<String>,
    ): Result<Unit> = safeApiCall {
        tagsApi.updateConversationTags(conversationId, tags)
    }
}
