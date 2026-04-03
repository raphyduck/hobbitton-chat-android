package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Prompt
import kotlinx.serialization.Serializable

@Serializable
data class PromptListResponse(
    val prompts: List<Prompt> = emptyList(),
)
