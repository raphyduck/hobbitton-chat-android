package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.network.engine.EngineHttpException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException

/**
 * Turns whatever the HTTP stack threw into something a screen can act on.
 *
 * Lives here, not in the feature module: Ktor's exception types are `:core:network`'s business, and
 * a feature that pattern-matches on them would be a feature that has to be updated when the client
 * library changes.
 */
fun Throwable.engineFailureKind(): EngineFailureKind = when (this) {
    // The status, checked at the call site before anything was decoded — the precise answer.
    is EngineHttpException -> when (status) {
        401 -> EngineFailureKind.AUTHENTICATION
        403 -> EngineFailureKind.PERMISSION
        404 -> EngineFailureKind.NOT_FOUND
        in 500..599 -> EngineFailureKind.SERVER
        else -> EngineFailureKind.UNKNOWN
    }

    is ClientRequestException -> when (response.status) {
        HttpStatusCode.Unauthorized -> EngineFailureKind.AUTHENTICATION
        HttpStatusCode.Forbidden -> EngineFailureKind.PERMISSION
        HttpStatusCode.NotFound -> EngineFailureKind.NOT_FOUND
        else -> EngineFailureKind.UNKNOWN
    }

    is ServerResponseException -> EngineFailureKind.SERVER

    // The body was not what the route promised. In front of a service behind a portal this is
    // almost always the portal itself answering — an HTML sign-in page where JSON was expected.
    // Calling it « authentication » is a judgement, and it is the useful one: the alternative is a
    // deserialization message that names a Kotlin type and no remedy.
    is NoTransformationFoundException -> EngineFailureKind.AUTHENTICATION

    is HttpRequestTimeoutException, is IOException -> EngineFailureKind.UNREACHABLE

    // A ResponseException the two branches above did not catch (a redirect surfaced as one, say).
    is ResponseException -> EngineFailureKind.UNKNOWN

    else -> EngineFailureKind.UNKNOWN
}
