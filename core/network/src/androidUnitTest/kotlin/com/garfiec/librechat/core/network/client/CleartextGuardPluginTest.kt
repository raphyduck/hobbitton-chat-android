package com.garfiec.librechat.core.network.client

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Where a request may go in the clear (finding M4, 26/09/2026): a private host, and nowhere else —
 * whether the URL was typed, handed back by the server, or reached through a redirect.
 */
class CleartextGuardPluginTest {

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(CleartextGuardPlugin)
    }

    @Test
    fun `http to a public host is refused before it is dialled`() = runTest {
        var dialled = 0
        val engine = MockEngine {
            dialled++
            respond("ok", HttpStatusCode.OK)
        }

        val refused = runCatching {
            client(engine).get("http://chat.example.com/api/files/download/1?sig=secret")
        }.exceptionOrNull()

        assertThat(dialled).isEqualTo(0)
        assertThat(refused).isInstanceOf(CleartextRefusedException::class.java)
        // The host names the problem; the query is where a presigned URL keeps its signature.
        assertThat((refused as CleartextRefusedException).host).isEqualTo("chat.example.com")
        assertThat(refused.message).doesNotContain("sig=")
    }

    @Test
    fun `http to a private host goes through`() = runTest {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }

        val response = client(engine).get("http://192.168.1.20:3080/api/config")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `https to a public host goes through`() = runTest {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }

        val response = client(engine).get("https://chat.example.com/api/config")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `a redirect from a private host towards a public http host is refused at the hop`() = runTest {
        // Ktor refuses an https→http downgrade on its own, but not an http→http hop — and a LAN
        // server's redirect lands in the same HttpSend interceptor as the first request.
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            seen += request.url.host
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "http://evil.example.net/steal"),
            )
        }

        val refused = runCatching { client(engine).get("http://192.168.1.20:3080/api/files/download/1") }
            .exceptionOrNull()

        assertThat(refused).isInstanceOf(CleartextRefusedException::class.java)
        assertThat(seen).containsExactly("192.168.1.20")
    }
}
