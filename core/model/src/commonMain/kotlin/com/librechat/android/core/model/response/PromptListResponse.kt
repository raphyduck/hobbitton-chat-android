package com.librechat.android.core.model.response

import com.librechat.android.core.model.Prompt
import kotlinx.serialization.Serializable

@Serializable
data class PromptListResponse(
    val prompts: List<Prompt> = emptyList(),
)
