package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.di.librechatJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a composer gets back from a transcription: the words, or a failure it can name. The HTTP
 * stack's exceptions stop here.
 */
class SchedulerTranscriberTest {

    private val audio = byteArrayOf(1, 2, 3)

    private fun transcriber(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"texte":"bonjour"}""",
        configured: Boolean = true,
        onRequest: () -> Unit = {},
    ) = SchedulerTranscriber(
        api = SchedulerApi(
            engineTestClient(
                MockEngine {
                    onRequest()
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            librechatJson,
        ),
        configured = { configured },
        language = { "fr" },
    )

    @Test
    fun `the words come back as heard`() = runTest {
        val outcome = transcriber().transcribe(audio, "audio/ogg", "dictee.ogg")

        assertEquals(TranscriptionOutcome.Heard("bonjour"), outcome)
    }

    @Test
    fun `a refused file keeps the server's reason`() = runTest {
        val outcome = transcriber(HttpStatusCode.BadRequest, """{"erreur":"type non audio"}""")
            .transcribe(audio, "audio/ogg", "dictee.ogg")

        assertEquals(TranscriptionOutcome.Failed(TranscriptionFailure.REJECTED, "type non audio"), outcome)
    }

    @Test
    fun `403 and 502 are told apart`() = runTest {
        val forbidden = transcriber(HttpStatusCode.Forbidden, "{}").transcribe(audio, "audio/ogg", "a.ogg")
        val down = transcriber(HttpStatusCode.BadGateway, """{"erreur":"indisponible"}""")
            .transcribe(audio, "audio/ogg", "a.ogg")

        assertEquals(TranscriptionFailure.PERMISSION, (forbidden as TranscriptionOutcome.Failed).failure)
        assertEquals(TranscriptionFailure.UNAVAILABLE, (down as TranscriptionOutcome.Failed).failure)
        assertEquals("indisponible", down.reason)
    }

    @Test
    fun `without a scheduler nothing is sent`() = runTest {
        var calls = 0
        val outcome = transcriber(configured = false, onRequest = { calls++ })
            .transcribe(audio, "audio/ogg", "a.ogg")

        assertEquals(TranscriptionOutcome.Failed(TranscriptionFailure.NOT_CONFIGURED), outcome)
        assertEquals(0, calls)
    }

    @Test
    fun `a device language is kept only as a bare ISO code`() {
        assertEquals("fr", isoLanguageOrNull("fr"))
        assertEquals("fr", isoLanguageOrNull("fr-FR"))
        assertEquals("pt", isoLanguageOrNull("pt_BR"))
        assertEquals("he", isoLanguageOrNull("iw"))
        assertNull(isoLanguageOrNull("fil"))
        assertNull(isoLanguageOrNull(""))
        assertNull(isoLanguageOrNull(null))
        assertEquals("en", isoLanguageOrNull("EN"))
    }
}
