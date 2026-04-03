package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.ChatRequest
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.NO_PARENT

object ChatPayloadBuilder {

    fun build(
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
    ): ChatRequest {
        val resolvedEndpoint = try {
            EModelEndpoint.valueOf(endpoint.uppercase())
        } catch (_: IllegalArgumentException) {
            EModelEndpoint.AGENTS
        }

        val resolvedParentMessageId = parentMessageId ?: NO_PARENT

        return ChatRequest(
            text = text,
            conversationId = conversationId,
            parentMessageId = resolvedParentMessageId,
            endpoint = resolvedEndpoint,
            model = model,
            agentId = agentId,
            overrideParentMessageId = overrideParentMessageId,
            responseMessageId = responseMessageId,
            isEdited = isEdited,
            isRegenerate = isRegenerate,
            isContinued = isContinued,
            webSearch = if (webSearch) true else null,
            files = files?.takeIf { it.isNotEmpty() },
            addedConvo = addedConvo,
            ephemeralAgent = ephemeralAgent,
        )
    }
}
