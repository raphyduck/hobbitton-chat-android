package com.librechat.android.core.network.sse

import com.librechat.android.core.model.StreamEvent
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
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

class SseClient @Inject constructor(
    private val json: Json,
) {
    private val mapper = SseEventMapper(json)
    private val lineParser = SseLineParser()

    fun connect(
        client: HttpClient,
        streamPath: String,
        resume: Boolean = false,
    ): Flow<StreamEvent> = flow {
        mapper.resetState()
        var attempt = 0
        var shouldResume = resume
        var done = false
        val maxRetries = 5
        val initialDelayMs = 1000L
        val maxDelayMs = 30_000L

        while (attempt <= maxRetries && !done) {
            try {
                // Use prepareGet + execute to stream the response incrementally.
                // client.get() buffers the entire body in memory before returning,
                // which defeats SSE streaming. prepareGet().execute {} keeps the
                // HTTP connection open so we can read line-by-line as data arrives.
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
                            attempt = 0 // Reset on successful connection
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
                            Timber.w("SSE: 401 Unauthorized for $streamPath")
                            emit(StreamEvent.Error(message = "Unauthorized", code = "401"))
                            done = true
                        }

                        else -> {
                            Timber.w("SSE: unexpected status ${response.status} for $streamPath")
                            attempt++
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                Timber.w(e, "SSE I/O error (attempt $attempt)")
                attempt++
                if (attempt > maxRetries) {
                    emit(StreamEvent.Error(message = "Connection lost. Please check your network and try again."))
                    done = true
                }
            } catch (e: Exception) {
                Timber.w(e, "SSE connection error (attempt $attempt)")
                attempt++
                if (attempt > maxRetries) {
                    emit(StreamEvent.Error(message = "Connection failed. Please try again."))
                    done = true
                }
            }

            if (!done) {
                val delayMs = min(initialDelayMs * (1L shl (attempt - 1)), maxDelayMs)
                delay(delayMs)
                shouldResume = true
            }
        }
    }
}
