package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.ConversationTag

interface TagRepository {
    suspend fun getTags(): Result<List<ConversationTag>>
    suspend fun updateConversationTags(conversationId: String, tags: List<String>): Result<Unit>
}
