package com.garfiec.librechat.core.network.engine

/**
 * The engine (or whatever answered in its place) returned a failing status.
 *
 * Raised **before** the body is decoded, and that is the whole point. Ktor's default is to hand a
 * 401 to the deserializer like any other response; a portal's HTML sign-in page then fails to
 * decode, and what reaches the screen is
 * `NoTransformationFoundException: Expected response body of the type 'interface java.util.Map'…`
 * — a Kotlin type name where the reader needed the number 401.
 *
 * `expectSuccess = true` would raise on the same responses, but it is validated by a plugin whose
 * ordering against [EngineAuthPlugin]'s renew-and-retry is an installation detail. Checking at the
 * call site is one line, ordering-independent, and cannot preempt a retry that would have worked.
 */
class EngineHttpException(
    val status: Int,
    val method: String,
    val path: String,
) : Exception("Engine answered HTTP $status for $method $path")
