package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.sse.SseHttpStatusException
import com.garfiec.librechat.core.network.sse.SseLineParser
import com.garfiec.librechat.core.network.sse.SseStreamException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * A live [EngineStreamEvent] flow for one session, kept open across drops.
 *
 * The engine's feed is global, so this subscribes once and keeps only the frames whose
 * `properties.sessionID` matches — every other session's traffic is dropped before it reaches the
 * screen. There is no resume cursor on the classic feed: a reconnect resumes at « now », which is why
 * the chat seeds its past from the transcript rather than from a replay.
 *
 * Intentionally lighter than [com.garfiec.librechat.core.network.sse.SseClient]: no account
 * origin-binding (the engine has one identity, applied by its own auth plugin) and no terminal
 * event — a session is never « done », the user simply leaves and the flow is cancelled.
 */
class EngineStreamClient(
    private val parser: EngineEventParser,
) {
    private val lineParser = SseLineParser()

    fun connect(sessionId: String, transport: EngineEventTransport): Flow<EngineStreamEvent> = flow {
        var attempt = 0

        while (true) {
            try {
                coroutineScope {
                    val byteChannel = ByteChannel(autoFlush = true)
                    val pump = launch {
                        try {
                            transport.stream().collect { bytes ->
                                attempt = 0
                                byteChannel.writeFully(bytes)
                            }
                            byteChannel.flushAndClose()
                        } catch (e: CancellationException) {
                            byteChannel.cancel(e)
                            throw e
                        } catch (e: Exception) {
                            byteChannel.cancel(e)
                            throw e
                        }
                    }
                    try {
                        lineParser.parse(byteChannel).collect { frame ->
                            val parsed = parser.parse(frame) ?: return@collect
                            // The feed carries every session; keep only the one on screen.
                            if (parsed.sessionId != null && parsed.sessionId != sessionId) return@collect
                            parsed.event?.let { emit(it) }
                        }
                    } finally {
                        pump.cancel()
                    }
                }
                // A clean end of response is the engine closing an idle connection, not the end of
                // the conversation. Reconnect after a pause.
                attempt++
            } catch (e: CancellationException) {
                throw e
            } catch (e: SseHttpStatusException) {
                when (e.statusCode) {
                    UNAUTHORIZED, FORBIDDEN -> {
                        Diag.w("EngineSSE", origin = LogOrigin.SERVER, attrs = mapOf("status" to e.statusCode.toString())) {
                            "engine event feed rejected"
                        }
                        return@flow
                    }
                    else -> attempt++
                }
            } catch (e: SseStreamException) {
                Diag.w("EngineSSE", origin = LogOrigin.NETWORK, throwable = e, attrs = mapOf("attempt" to attempt.toString())) {
                    "engine event feed I/O error"
                }
                attempt++
            } catch (e: Exception) {
                Diag.w("EngineSSE", origin = LogOrigin.NETWORK, throwable = e, attrs = mapOf("attempt" to attempt.toString())) {
                    "engine event feed error"
                }
                attempt++
            }

            if (attempt > MAX_RETRIES) return@flow
            val backoff = min(INITIAL_DELAY_MS * (1L shl (attempt - 1).coerceAtLeast(0)), MAX_DELAY_MS)
            delay(backoff)
        }
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val INITIAL_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }
}
