package com.librechat.android.core.network.api

import com.librechat.android.core.model.Message
import com.librechat.android.core.model.request.BranchMessageRequest
import com.librechat.android.core.model.request.FeedbackRequest
import com.librechat.android.core.model.request.SaveMessageRequest
import com.librechat.android.core.model.request.UpdateArtifactRequest
import com.librechat.android.core.model.request.UpdateMessageRequest
import com.librechat.android.core.model.response.MessageListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

class MessagesApi constructor(
    private val client: HttpClient,
) {
    suspend fun getMessages(conversationId: String): List<Message> =
        client.get {
            url { path("api/messages/$conversationId") }
        }.body()

    suspend fun getMessage(conversationId: String, messageId: String): Message =
        client.get {
            url { path("api/messages/$conversationId/$messageId") }
        }.body()

    suspend fun saveMessage(conversationId: String, request: SaveMessageRequest): Message =
        client.post {
            url { path("api/messages/$conversationId") }
            setBody(request)
        }.body()

    suspend fun updateMessage(conversationId: String, messageId: String, request: UpdateMessageRequest): Message =
        client.put {
            url { path("api/messages/$conversationId/$messageId") }
            setBody(request)
        }.body()

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        client.delete {
            url { path("api/messages/$conversationId/$messageId") }
        }
    }

    suspend fun branchMessage(request: BranchMessageRequest): Message =
        client.post {
            url { path("api/messages/branch") }
            setBody(request)
        }.body()

    suspend fun updateFeedback(conversationId: String, messageId: String, feedback: String?) {
        client.put {
            url { path("api/messages/$conversationId/$messageId/feedback") }
            setBody(FeedbackRequest(feedback = feedback))
        }
    }

    suspend fun getGlobalMessages(
        cursor: String? = null,
        limit: Int = 25,
        search: String? = null,
    ): MessageListResponse =
        client.get {
            url { path("api/messages") }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
            search?.let { parameter("search", it) }
        }.body()

    suspend fun updateArtifact(messageId: String, request: UpdateArtifactRequest): Message =
        client.post {
            url { path("api/messages/artifact/$messageId") }
            setBody(request)
        }.body()
}
