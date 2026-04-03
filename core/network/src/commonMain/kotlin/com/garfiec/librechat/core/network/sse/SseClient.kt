package com.garfiec.librechat.core.network.sse

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.StreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.math.min

class SseClient(
    private val json: Json,
) {
    private val mapper = SseEventMapper(json)
    private val lineParser = SseLineParser()

    /**
     * @param connectivityFlow Optional flow of network connectivity state. When provided,
     *   retries will wait for the network to become available before attempting reconnection,
     *   avoiding wasted retry attempts while offline.
     */
    fun connect(
        client: HttpClient,
        streamPath: String,
        resume: Boolean = false,
        connectivityFlow: Flow<Boolean>? = null,
    ): Flow<StreamEvent> = flow {
        // Outer try/catch ensures no exception escapes the flow.
        // SKIE's Flow→AsyncSequence iterator calls fatalError on unexpected errors,
        // so any Kotlin exception that escapes this flow will crash the iOS app.
        try {
        mapper.resetState()
        var attempt = 0
        var shouldResume = resume
        var done = false
        val maxRetries = 5
        val initialDelayMs = 1000L
        val maxDelayMs = 30_000L

        while (attempt <= maxRetries && !done) {
            try {
                val statement = client.prepareGet {
                    url { path(streamPath) }
                    accept(ContentType.Text.EventStream)
                    if (shouldResume) {
                        parameter("resume", "true")
                    }
                }

                statement.execute { response ->
                    when {
                        response.status.isSuccess() -> {
                            attempt = 0
                            val channel = response.bodyAsChannel()
                            lineParser.parse(channel).collect { sseEvent ->
                                val streamEvent = mapper.map(sseEvent)
                                if (streamEvent != null) {
                                    emit(streamEvent)
                                    if (streamEvent is StreamEvent.Final) {
                                        done = true
                                    }
                                }
                            }
                            done = true
                        }

                        response.status == HttpStatusCode.NotFound -> {
                            done = true
                        }

                        response.status == HttpStatusCode.Unauthorized -> {
                            Logger.w("SSE") { "SSE: 401 Unauthorized for $streamPath" }
                            emit(StreamEvent.Error(message = "Unauthorized", code = "401"))
                            done = true
                        }

                        else -> {
                            Logger.w("SSE") { "SSE: unexpected status ${response.status} for $streamPath" }
                            attempt++
                        }
                    }
                }
            } catch (e: SseStreamException) {
                Logger.w("SSE", e) { "SSE I/O error (attempt $attempt)" }
                attempt++
                if (attempt > maxRetries) {
                    emit(StreamEvent.Error(message = "Connection lost. Please check your network and try again.", isNetworkError = true))
                    done = true
                }
            } catch (e: Exception) {
                Logger.w("SSE", e) { "SSE connection error (attempt $attempt)" }
                attempt++
                if (attempt > maxRetries) {
                    emit(StreamEvent.Error(message = "Connection failed. Please try again."))
                    done = true
                }
            }

            if (!done) {
                emit(StreamEvent.Retrying(attempt = attempt, maxAttempts = maxRetries))

                if (connectivityFlow != null) {
                    try {
                        val isConnected = connectivityFlow.first()
                        if (!isConnected) {
                            Logger.d("SSE") { "SSE: network is down, waiting for connectivity before retry $attempt" }
                            connectivityFlow.first { it }
                            Logger.d("SSE") { "SSE: network restored, proceeding with retry $attempt" }
                        }
                    } catch (e: Exception) {
                        Logger.w("SSE", e) { "SSE: error checking connectivity, falling back to delay" }
                    }
                }

                val delayMs = min(initialDelayMs * (1L shl (attempt - 1)), maxDelayMs)
                delay(delayMs)
                shouldResume = true
            }
        }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // Re-throw cancellation — SKIE handles this gracefully
        } catch (e: Exception) {
            Logger.e("SSE", e) { "SSE: unhandled exception escaped flow" }
            emit(StreamEvent.Error(message = "Unexpected error: ${e.message}"))
        }
    }
}
