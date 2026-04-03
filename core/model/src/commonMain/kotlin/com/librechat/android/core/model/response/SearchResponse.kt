package com.librechat.android.core.model.response

import com.librechat.android.core.model.Conversation
import com.librechat.android.core.model.Message
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val pageNumber: Int? = null,
    val pageSize: Int? = null,
    val pages: Int? = null,
)
