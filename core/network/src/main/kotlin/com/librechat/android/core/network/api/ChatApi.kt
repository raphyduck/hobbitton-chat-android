package com.librechat.android.core.network.api

import com.librechat.android.core.model.request.ChatAbortRequest
import com.librechat.android.core.model.request.ChatRequest
import com.librechat.android.core.model.response.ActiveJobsResponse
import com.librechat.android.core.model.response.ChatAbortResponse
import com.librechat.android.core.model.response.ChatStartResponse
import com.librechat.android.core.model.response.ChatStatusResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path
import javax.inject.Inject

class ChatApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun startChat(endpoint: String, request: ChatRequest): ChatStartResponse =
        client.post {
            url { path("api/agents/chat/$endpoint") }
            setBody(request)
        }.body()

    suspend fun abortChat(streamId: String): ChatAbortResponse =
        client.post {
            url { path("api/agents/chat/abort") }
            setBody(ChatAbortRequest(abortKey = streamId, endpoint = "agents"))
        }.body()

    suspend fun getActiveJobs(): ActiveJobsResponse =
        client.get {
            url { path("api/agents/chat/active") }
        }.body()

    suspend fun getChatStatus(conversationId: String): ChatStatusResponse =
        client.get {
            url { path("api/agents/chat/status/$conversationId") }
        }.body()
}
