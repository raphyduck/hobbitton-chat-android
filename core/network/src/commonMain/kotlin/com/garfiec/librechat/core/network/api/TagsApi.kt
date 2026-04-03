package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.request.CreateTagRequest
import com.garfiec.librechat.core.model.request.TagUpdateRequest
import com.garfiec.librechat.core.model.request.UpdateTagRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

class TagsApi constructor(
    private val client: HttpClient,
) {
    suspend fun getTags(): List<ConversationTag> =
        client.get {
            url { path("api/tags") }
        }.body()

    suspend fun createTag(request: CreateTagRequest): ConversationTag =
        client.post {
            url { path("api/tags") }
            setBody(request)
        }.body()

    suspend fun updateTag(tag: String, request: UpdateTagRequest): ConversationTag =
        client.put {
            url { path("api/tags/$tag") }
            setBody(request)
        }.body()

    suspend fun deleteTag(tag: String) {
        client.delete {
            url { path("api/tags/$tag") }
        }
    }

    suspend fun updateConversationTags(conversationId: String, tags: List<String>) {
        client.put {
            url { path("api/tags/convo/$conversationId") }
            setBody(TagUpdateRequest(tags = tags))
        }
    }
}
