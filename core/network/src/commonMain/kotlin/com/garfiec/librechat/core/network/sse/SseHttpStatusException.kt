package com.garfiec.librechat.core.network.sse

/**
 * Thrown by an event-stream transport when the server responds with a non-success status code.
 * Preserves the numeric status so the stream client can branch on 401 / 404 / other.
 */
class SseHttpStatusException(
    val statusCode: Int,
    message: String = "SSE transport received HTTP $statusCode",
) : Exception(message)
