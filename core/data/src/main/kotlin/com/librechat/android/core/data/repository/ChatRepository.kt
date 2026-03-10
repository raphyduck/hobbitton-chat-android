package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.FileReference
import com.librechat.android.core.model.StreamEvent
import com.librechat.android.core.model.request.AddedConversation
import com.librechat.android.core.model.request.EphemeralAgent
import com.librechat.android.core.model.response.ChatStatusResponse
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun startChat(
        text: String,
        conversationId: String?,
        endpoint: String,
        model: String?,
        parentMessageId: String? = null,
        agentId: String? = null,
        overrideParentMessageId: String? = null,
        responseMessageId: String? = null,
        isEdited: Boolean = false,
        isRegenerate: Boolean = false,
        isContinued: Boolean = false,
        webSearch: Boolean = false,
        files: List<FileReference>? = null,
        addedConvo: AddedConversation? = null,
        ephemeralAgent: EphemeralAgent? = null,
    ): Flow<StreamEvent>
    suspend fun abortChat(streamId: String): Result<Unit>
    suspend fun checkStreamStatus(conversationId: String): ChatStatusResponse
    fun resumeStream(conversationId: String): Flow<StreamEvent>
}
