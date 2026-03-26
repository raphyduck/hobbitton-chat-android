package com.librechat.android.core.data.repository

import com.librechat.android.core.common.network.ConnectivityObserver
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.FileReference
import com.librechat.android.core.model.StreamEvent
import com.librechat.android.core.model.request.AddedConversation
import com.librechat.android.core.model.request.ChatAbortRequest
import com.librechat.android.core.model.request.ChatRequest
import com.librechat.android.core.model.request.EphemeralAgent
import com.librechat.android.core.model.response.ChatStatusResponse
import com.librechat.android.core.network.api.ChatApi
import com.librechat.android.core.network.di.StreamingClient
import com.librechat.android.core.network.sse.SseClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val sseClient: SseClient,
    @StreamingClient private val sseHttpClient: HttpClient,
    private val connectivityObserver: ConnectivityObserver,
) : ChatRepository {

    override fun startChat(
        text: String,
        conversationId: String?,
        endpoint: String,
        model: String?,
        parentMessageId: String?,
        agentId: String?,
        overrideParentMessageId: String?,
        responseMessageId: String?,
        isEdited: Boolean,
        isRegenerate: Boolean,
        isContinued: Boolean,
        webSearch: Boolean,
        files: List<FileReference>?,
        addedConvo: AddedConversation?,
        ephemeralAgent: EphemeralAgent?,
    ): Flow<StreamEvent> = flow {
        // Phase 1: POST to start the chat - get back a streamId (= conversationId)
        val request = ChatPayloadBuilder.build(
            text = text,
            conversationId = conversationId,
            endpoint = endpoint,
            model = model,
            parentMessageId = parentMessageId,
            agentId = agentId,
            overrideParentMessageId = overrideParentMessageId,
            responseMessageId = responseMessageId,
            isEdited = isEdited,
            isRegenerate = isRegenerate,
            isContinued = isContinued,
            webSearch = webSearch,
            files = files,
            addedConvo = addedConvo,
            ephemeralAgent = ephemeralAgent,
        )
        val startResponse = chatApi.startChat(endpoint, request)
        val streamId = startResponse.conversationId

        // Emit a Created event from the POST response so the ViewModel
        // knows the conversationId before any SSE events arrive.
        emit(StreamEvent.Created(
            conversationId = streamId,
            messageId = "",
            parentMessageId = "",
        ))

        // Phase 2: GET the SSE stream using the streamId
        val streamUrl = "api/agents/chat/stream/$streamId"
        emitAll(sseClient.connect(sseHttpClient, streamUrl, connectivityFlow = connectivityObserver.isConnected))
    }

    override suspend fun abortChat(streamId: String): Result<Unit> = safeApiCall {
        chatApi.abortChat(streamId)
    }

    override suspend fun checkStreamStatus(conversationId: String): ChatStatusResponse {
        return chatApi.getChatStatus(conversationId)
    }

    override fun resumeStream(conversationId: String): Flow<StreamEvent> = flow {
        val streamUrl = "api/agents/chat/stream/$conversationId"
        emitAll(sseClient.connect(sseHttpClient, streamUrl, resume = true, connectivityFlow = connectivityObserver.isConnected))
    }
}
