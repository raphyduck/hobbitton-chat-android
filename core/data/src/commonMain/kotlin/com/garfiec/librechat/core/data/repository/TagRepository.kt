package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ConversationTag

interface TagRepository {
    suspend fun getTags(): Result<List<ConversationTag>>
    suspend fun updateConversationTags(conversationId: String, tags: List<String>): Result<Unit>
}
