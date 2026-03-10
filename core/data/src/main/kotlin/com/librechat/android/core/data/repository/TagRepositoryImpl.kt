package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.ConversationTag
import com.librechat.android.core.network.api.TagsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
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
