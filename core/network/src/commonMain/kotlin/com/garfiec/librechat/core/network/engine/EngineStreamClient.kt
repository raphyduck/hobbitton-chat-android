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

            // Les essais rapides sont épuisés. On ne rend PAS la main : un flux qui se termine
            // laisse l'écran sourd sans un mot, et c'est indiscernable d'un agent qui a cessé de
            // répondre — la conversation continue côté moteur, l'appareil ne l'entend plus.
            // Constaté le 17/09/2026 sur une session de mission qui « ne répondait plus » ; elle
            // répondait, et rouvrir la session suffisait à tout faire apparaître.
            //
            // Une panne assez longue pour brûler les cinq essais (~31 s de temporisation cumulée)
            // est un tunnel, un changement de réseau, une mise en veille — toutes choses qui
            // passent. On repart donc à zéro après une pause franche, aussi longtemps que l'écran
            // est ouvert. C'est l'annulation du collecteur, et elle seule, qui arrête ce flux.
            if (attempt > MAX_RETRIES) {
                Diag.w("EngineSSE", origin = LogOrigin.NETWORK, attrs = mapOf("attempt" to attempt.toString())) {
                    "engine event feed exhausted its fast retries, pausing before a fresh attempt"
                }
                delay(COOLDOWN_MS)
                attempt = 0
                continue
            }
            val backoff = min(INITIAL_DELAY_MS * (1L shl (attempt - 1).coerceAtLeast(0)), MAX_DELAY_MS)
            delay(backoff)
        }
    }

    private companion object {
        /**
         * Combien d'essais rapprochés avant de souffler. Ce n'est plus un budget de vie : au-delà
         * on attend [COOLDOWN_MS] et on recommence, parce qu'abandonner pour de bon est le seul
         * échec que l'utilisateur ne peut pas voir.
         */
        const val MAX_RETRIES = 5
        const val INITIAL_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L

        /**
         * La pause entre deux salves. Assez longue pour ne pas marteler un moteur éteint ni vider
         * la batterie, assez courte pour qu'une sortie de tunnel se rattrape sans que personne
         * n'ait à toucher l'écran.
         */
        const val COOLDOWN_MS = 60_000L
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }
}
