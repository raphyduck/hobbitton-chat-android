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
 * It stitches together the pieces around [EngineEventTransport]: pump the raw bytes into a channel,
 * let [SseLineParser] frame them, let [EngineEventParser] map each frame, and forward the mapped
 * events. Because the engine feed is *durable*, a dropped connection is not a lost conversation — the
 * client reconnects with `?after=<last seq>` and the engine resumes exactly where it left off, so no
 * event is replayed twice and none is missed. That is the whole reason to track the seq.
 *
 * This is intentionally lighter than [com.garfiec.librechat.core.network.sse.SseClient]: no account
 * origin-binding (the engine has one identity, applied by its own auth plugin) and no `[DONE]`/final
 * terminus (a chat session never "ends" — the user leaves and cancels the flow). What it keeps is the
 * reconnect ladder and the typed-status branching, because those are what make a phone on a flaky
 * network still show the reply.
 */
class EngineStreamClient(
    private val parser: EngineEventParser,
) {
    private val lineParser = SseLineParser()

    fun connect(sessionId: String, transport: EngineEventTransport): Flow<EngineStreamEvent> = flow {
        var lastSeq: Long? = null
        var attempt = 0

        while (true) {
            try {
                coroutineScope {
                    val byteChannel = ByteChannel(autoFlush = true)
                    val pump = launch {
                        try {
                            transport.stream(sessionId, lastSeq?.toString()).collect { bytes ->
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
                            parsed.seq?.let { lastSeq = it }
                            parsed.event?.let { emit(it) }
                        }
                    } finally {
                        pump.cancel()
                    }
                }
                // Clean end of the HTTP response: the engine closed an idle connection, not the
                // conversation. Reconnect from the cursor after a short pause.
                attempt++
            } catch (e: CancellationException) {
                throw e
            } catch (e: SseHttpStatusException) {
                when (e.statusCode) {
                    NOT_FOUND -> {
                        emit(EngineStreamEvent.Failed(null, "session not found"))
                        return@flow
                    }
                    UNAUTHORIZED, FORBIDDEN -> {
                        Diag.w("EngineSSE", origin = LogOrigin.SERVER, attrs = mapOf("status" to e.statusCode.toString())) {
                            "engine event stream rejected"
                        }
                        emit(EngineStreamEvent.Failed(null, "not authorized"))
                        return@flow
                    }
                    else -> attempt++
                }
            } catch (e: SseStreamException) {
                Diag.w("EngineSSE", origin = LogOrigin.NETWORK, throwable = e, attrs = mapOf("attempt" to attempt.toString())) {
                    "engine event stream I/O error"
                }
                attempt++
            } catch (e: Exception) {
                Diag.w("EngineSSE", origin = LogOrigin.NETWORK, throwable = e, attrs = mapOf("attempt" to attempt.toString())) {
                    "engine event stream error"
                }
                attempt++
            }

            if (attempt > MAX_RETRIES) {
                emit(EngineStreamEvent.Failed(null, "connection lost"))
                return@flow
            }
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
        const val NOT_FOUND = 404
    }
}
