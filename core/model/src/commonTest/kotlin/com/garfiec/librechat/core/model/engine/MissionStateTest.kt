package com.garfiec.librechat.core.model.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The rule that decides whether a mission worked. Its whole reason to exist is a night that was
 * counted as a success and was not — so these tests are written from that night's actual payloads.
 */
class MissionStateTest {

    private fun message(
        role: String = "assistant",
        input: Long = 0,
        output: Long = 0,
        cacheRead: Long = 0,
        error: EngineError? = null,
    ) = EngineMessage(
        info = EngineMessageInfo(
            id = "m",
            role = role,
            tokens = EngineTokens(
                input = input,
                output = output,
                cache = EngineCacheTokens(read = cacheRead),
            ),
            error = error,
        ),
    )

    @Test
    fun `a session listed as active is running`() {
        val state = judgeMission(
            status = EngineSessionStatus(type = "retry", message = "Cannot connect to API", attempt = 5),
            messages = listOf(message(input = 10)),
        )

        assertEquals(MissionState.Running("Cannot connect to API"), state)
    }

    @Test
    fun `absent from the status map plus real tokens is a success`() {
        val state = judgeMission(status = null, messages = listOf(message(input = 100, output = 20)))

        assertEquals(MissionState.Succeeded(120), state)
    }

    @Test
    fun `the night of 21 august is a failure, not a success`() {
        // The gateway had lost its database. The engine filed the error on the message and let the
        // session fall idle in 11 milliseconds; the scheduler recorded OK, 3,0 s, 0 token.
        val state = judgeMission(
            status = null,
            messages = listOf(
                message(role = "user"),
                message(
                    error = EngineError(
                        name = "UnknownError",
                        data = EngineErrorData(message = """{"error":{"message":"No connected db."}}"""),
                    ),
                ),
            ),
        )

        val failed = assertIs<MissionState.Failed>(state)
        assertEquals(0, failed.tokens)
        // The reason is shown as-is, so it has to be readable rather than three layers of JSON.
        assertEquals(true, failed.reason.contains("No connected db"))
    }

    @Test
    fun `zero tokens is a failure even when the engine filed no error`() {
        // The backstop. It does not depend on how the engine words its failures today.
        val state = judgeMission(status = null, messages = listOf(message(role = "user"), message()))

        val failed = assertIs<MissionState.Failed>(state)
        assertEquals(true, failed.reason.contains("aucun appel modèle"))
    }

    @Test
    fun `cache tokens count as work done`() {
        // A long mission is mostly cache reads. Counting only input+output would call it dead.
        val state = judgeMission(status = null, messages = listOf(message(cacheRead = 5_000)))

        assertEquals(MissionState.Succeeded(5_000), state)
    }

    @Test
    fun `a session with no messages yet is still starting, not failed`() {
        // Between POST /session and the first message there is a window where everything is empty.
        // Calling that a failure would make every launch flash red.
        assertIs<MissionState.Running>(judgeMission(status = null, messages = emptyList()))
    }

    @Test
    fun `a mission with no session at all is idle`() {
        assertEquals(MissionState.Idle, judgeMission(null, emptyList(), hasSession = false))
    }

    @Test
    fun `an explicitly idle status is treated as stopped, not running`() {
        val state = judgeMission(
            status = EngineSessionStatus(type = "idle"),
            messages = listOf(message(input = 5)),
        )

        assertEquals(MissionState.Succeeded(5), state)
    }

    @Test
    fun `the last error wins, and no error reads as none`() {
        assertNull(engineError(listOf(message(input = 3))))
        val messages = listOf(
            message(error = EngineError(name = "First", data = EngineErrorData("premiere"))),
            message(error = EngineError(name = "Second", data = EngineErrorData("derniere"))),
        )
        assertEquals("derniere", engineError(messages))
    }
}
