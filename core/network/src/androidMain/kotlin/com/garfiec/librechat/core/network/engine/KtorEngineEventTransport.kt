package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.sse.SseHttpStatusException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The engine event transport over the engine's own Ktor client (`KoinQualifiers.Engine`), so every
 * request carries the engine's Basic auth and the Authelia bearer that `EngineAuthPlugin` applies —
 * the same client [com.garfiec.librechat.core.network.api.AgentEngineApi] speaks through.
 *
 * `prepareGet + execute` streams the body incrementally; `client.get()` would buffer the whole
 * response first and defeat the point (the LibreChat Android SSE transport does the same for the same
 * reason — commit f182b2b). Two things differ from an ordinary engine call:
 *
 *  - **The request timeout is lifted.** The engine client caps requests at 30 s because every other
 *    call is small; a durable event feed is a long poll and would otherwise be aborted mid-reply.
 *    Stalls are still caught — [com.garfiec.librechat.core.network.sse.SseLineParser] fails a stream
 *    that goes quiet for its line-read timeout, which is what triggers the client's reconnect.
 *  - **`?after=` resumes.** The engine replays durable events after that seq, so a reconnect neither
 *    misses nor repeats an event.
 */
class KtorEngineEventTransport(
    private val client: HttpClient,
) : EngineEventTransport {

    override fun stream(sessionId: String, after: String?): Flow<ByteArray> = flow {
        client.prepareGet {
            url { path("api/session/${sessionId.encodeURLPathPart()}/event") }
            accept(ContentType.Text.EventStream)
            if (after != null) {
                parameter("after", after)
            }
            timeout {
                // A durable feed is a long poll: the engine client's 30 s request cap would abort it
                // mid-reply. Stalls are still caught by SseLineParser's line-read timeout, which is
                // what makes EngineStreamClient reconnect. Matches how the LibreChat streaming client
                // lifts the same two caps (NetworkModule).
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw SseHttpStatusException(response.status.value)
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read > 0) {
                    emit(buffer.copyOf(read))
                } else if (read < 0) {
                    break
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
