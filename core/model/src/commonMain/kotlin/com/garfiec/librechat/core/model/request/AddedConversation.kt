package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.model.EModelEndpoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddedConversation(
    val conversationId: String? = null,
    val parentMessageId: String? = null,
    val endpoint: EModelEndpoint? = null,
    val endpointType: EModelEndpoint? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val model: String? = null,
    val modelLabel: String? = null,
    val spec: String? = null,
)
