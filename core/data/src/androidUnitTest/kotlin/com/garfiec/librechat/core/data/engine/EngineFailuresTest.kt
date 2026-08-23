package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.network.engine.EngineHttpException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The classification the Tasks tab reads its sentence from.
 *
 * The case that matters is the first one: on 22 August a plain 401 reached the screen as
 * `NoTransformationFoundException: Expected response body of the type 'interface java.util.Map'…`,
 * because the portal answered with HTML and the deserializer spoke before anyone looked at the
 * status.
 */
class EngineFailuresTest {

    private fun http(status: Int) =
        EngineHttpException(status = status, method = "GET", path = "/session/status")

    @Test
    fun `a 401 is an authentication problem`() {
        assertEquals(EngineFailureKind.AUTHENTICATION, http(401).engineFailureKind())
    }

    @Test
    fun `a 403 is refused, not unauthenticated`() {
        // Different remedy: signing in again cannot help, so the screen must not offer it.
        assertEquals(EngineFailureKind.PERMISSION, http(403).engineFailureKind())
    }

    @Test
    fun `a 404 points at the address, not the session`() {
        assertEquals(EngineFailureKind.NOT_FOUND, http(404).engineFailureKind())
    }

    @Test
    fun `a 500 is the engine's own fault`() {
        assertEquals(EngineFailureKind.SERVER, http(503).engineFailureKind())
    }

    @Test
    fun `an unexpected status stays unknown rather than being guessed`() {
        assertEquals(EngineFailureKind.UNKNOWN, http(418).engineFailureKind())
    }

    @Test
    fun `a body that would not decode is read as a portal answering`() {
        // Belt and braces: the call sites check the status first, so this should no longer happen —
        // but if a route is ever added without that check, « sign in again » beats a Kotlin type
        // name on screen.
        val failure = NoTransformationFoundException(
            response = io.mockk.mockk(relaxed = true),
            from = String::class,
            to = Map::class,
        )

        assertEquals(EngineFailureKind.AUTHENTICATION, failure.engineFailureKind())
    }

    @Test
    fun `a transport failure is worth retrying`() {
        assertEquals(EngineFailureKind.UNREACHABLE, IOException("no route to host").engineFailureKind())
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(EngineFailureKind.UNKNOWN, IllegalStateException("boom").engineFailureKind())
    }
}
