package com.garfiec.librechat.core.common.result

/**
 * Exception thrown when the server returns a non-2xx HTTP response.
 * The [message] field contains a user-friendly error extracted from the response body.
 */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val isBanned: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
