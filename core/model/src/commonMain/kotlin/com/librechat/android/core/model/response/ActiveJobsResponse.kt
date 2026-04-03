package com.librechat.android.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ActiveJob(
    val conversationId: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
)

@Serializable
data class ActiveJobsResponse(
    val jobs: List<ActiveJob> = emptyList(),
)
