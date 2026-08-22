package com.garfiec.librechat.core.model.engine

/**
 * Why a call to the engine failed, in the only terms that change what a person should do next.
 *
 * A raw exception is not one of those terms. `NoTransformationFoundException: Expected response body
 * of the type 'interface java.util.Map' … but was 'class io.ktor.utils.io.SourceByteReadChannel'`
 * is what the Tasks tab showed on 22 August for what was, on the wire, a plain **401** — the portal
 * answering with HTML where the engine would have answered with JSON. Everything a reader needs was
 * in the status code, and none of it survived to the screen.
 */
enum class EngineFailureKind {
    /** No valid token: the portal answered instead of the engine. Sign in again, or fix the settings. */
    AUTHENTICATION,

    /** Authenticated, and not allowed. Nothing the app can retry its way out of. */
    PERMISSION,

    /** The host answered, the route did not exist — usually a base URL pointing at the wrong service. */
    NOT_FOUND,

    /** Nothing answered: no network, wrong host, service down. Retrying is the right move. */
    UNREACHABLE,

    /** The engine answered with a failure of its own. */
    SERVER,

    UNKNOWN,
}
