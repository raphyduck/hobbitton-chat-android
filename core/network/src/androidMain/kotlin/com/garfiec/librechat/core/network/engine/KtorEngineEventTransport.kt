package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.sse.SseHttpStatusException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The engine event transport over the engine's own Ktor client (`KoinQualifiers.Engine`), so every
 * request carries the engine's Basic auth and the Authelia bearer that `EngineAuthPlugin` applies.
 *
 * `prepareGet + execute` streams the body incrementally; `client.get()` would buffer the whole
 * response and defeat the point (the LibreChat Android SSE transport does the same, for the same
 * reason — commit f182b2b). The request timeout is lifted because a feed is a long poll: the engine
 * client caps ordinary requests at 30 s, which would cut a stream mid-answer. A stalled feed is still
 * caught by [com.garfiec.librechat.core.network.sse.SseLineParser]'s line-read timeout, which is what
 * drives the reconnect.
 */
class KtorEngineEventTransport(
    private val client: HttpClient,
) : EngineEventTransport {

    override fun stream(): Flow<ByteArray> = flow {
        client.prepareGet {
            url { path("event") }
            accept(ContentType.Text.EventStream)
            timeout {
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
