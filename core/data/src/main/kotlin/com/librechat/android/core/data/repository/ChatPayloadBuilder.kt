package com.librechat.android.core.data.repository

import com.librechat.android.core.model.EModelEndpoint
import com.librechat.android.core.model.FileReference
import com.librechat.android.core.model.request.AddedConversation
import com.librechat.android.core.model.request.ChatRequest
import com.librechat.android.core.model.request.EphemeralAgent
import com.librechat.android.core.model.request.NO_PARENT
import timber.log.Timber

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

        Timber.d("[Comparison] ChatPayloadBuilder.build: addedConvo=%s, endpoint=%s, model=%s, agentId=%s",
            if (addedConvo != null) "present(endpoint=${addedConvo.endpoint}, model=${addedConvo.model}, agentId=${addedConvo.agentId})" else "null",
            resolvedEndpoint, model, agentId)

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
