package com.garfiec.librechat.core.network.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * `POST /transcription`, the scheduler's one multipart route, pinned at the request level: the
 * path, the form, the file part the server looks for, and both shapes of answer.
 */
class SchedulerTranscriptionTest {

    private val audio = byteArrayOf(0x1A, 0x45, 0x2B, 0x7F)

    @Test
    fun `the audio leaves as a multipart file part, the language beside it`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var bodyType: ContentType? = null
        var sent = ""
        val api = schedulerApi(MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            bodyType = request.body.contentType
            sent = String(request.body.toByteArray(), Charsets.ISO_8859_1)
            respond(content = """{"texte":"Bonjour tout le monde"}""", headers = jsonHeaders())
        })

        val heard = api.transcribe(audio, mime = "audio/mp4", filename = "memo.m4a", language = "fr")

        assertThat(heard).isEqualTo("Bonjour tout le monde")
        assertThat(method).isEqualTo(HttpMethod.Post)
        assertThat(path).isEqualTo("/transcription")
        assertThat(bodyType?.withoutParameters()).isEqualTo(ContentType.MultiPart.FormData)
        // The file part: named `audio`, with its real name and its real type, then the bytes.
        assertThat(sent).containsMatch("name=\"?audio\"?")
        assertThat(sent).contains("filename=\"memo.m4a\"")
        assertThat(sent).contains("Content-Type: audio/mp4")
        assertThat(sent).contains(String(audio, Charsets.ISO_8859_1))
        // The language, as a plain text field.
        assertThat(sent).containsMatch("name=\"?langue\"?")
        assertThat(sent).contains("\r\n\r\nfr\r\n")
    }

    @Test
    fun `no language, no langue field`() = runTest {
        var sent = ""
        val api = schedulerApi(MockEngine { request ->
            sent = String(request.body.toByteArray(), Charsets.ISO_8859_1)
            respond(content = """{"texte":"ok"}""", headers = jsonHeaders())
        })

        api.transcribe(audio, mime = "audio/ogg", filename = "dictee.ogg")

        assertThat(sent).containsMatch("name=\"?audio\"?")
        assertThat(sent).doesNotContain("langue")
    }

    @Test
    fun `a file the server refuses carries its status and its own words`() = runTest {
        val api = schedulerApi(MockEngine {
            respond(
                content = """{"erreur":"fichier trop volumineux (25 Mo maximum)"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        })

        val refused = assertFailsWith<TranscriptionRefused> {
            api.transcribe(audio, mime = "audio/mpeg", filename = "long.mp3")
        }

        assertThat(refused.status).isEqualTo(400)
        assertThat(refused.reason).isEqualTo("fichier trop volumineux (25 Mo maximum)")
    }

    @Test
    fun `a transcription service that is down reads as a 502 with its reason`() = runTest {
        val api = schedulerApi(MockEngine {
            respond(
                content = """{"erreur":"transcription indisponible"}""",
                status = HttpStatusCode.BadGateway,
                headers = jsonHeaders(),
            )
        })

        val refused = assertFailsWith<TranscriptionRefused> {
            api.transcribe(audio, mime = "audio/wav", filename = "note.wav")
        }

        assertThat(refused.status).isEqualTo(502)
        assertThat(refused.reason).isEqualTo("transcription indisponible")
    }

    /** A 403 comes from the edge as often as from the route: no JSON, and still a refusal. */
    @Test
    fun `a refusal without a JSON body still says its status`() = runTest {
        val api = schedulerApi(MockEngine {
            respond(content = "<html>Forbidden</html>", status = HttpStatusCode.Forbidden)
        })

        val refused = assertFailsWith<TranscriptionRefused> {
            api.transcribe(audio, mime = "audio/webm", filename = "note.webm")
        }

        assertThat(refused.status).isEqualTo(403)
        assertThat(refused.reason).isNull()
    }

    @Test
    fun `a success without text is not taken for an empty transcription`() = runTest {
        val api = schedulerApi(MockEngine {
            respond(content = """{"autre":"chose"}""", headers = jsonHeaders())
        })

        assertFailsWith<TranscriptionRefused> {
            api.transcribe(audio, mime = "audio/mp4", filename = "memo.m4a")
        }
    }
}
