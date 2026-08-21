package com.garfiec.librechat.core.network.engine.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What lands on the loopback socket. The socket is open to every process on the device for the few
 * seconds the exchange lasts, so what it refuses matters as much as what it accepts.
 */
class FormPostCallbackTest {

    private fun request(
        method: String = "POST",
        path: String = "/oauth/authelia",
        body: String = "code=abc&state=xyz",
        separator: String = "\r\n\r\n",
    ) = "$method $path HTTP/1.1\r\nHost: 127.0.0.1:41234\r\n" +
        "Content-Type: application/x-www-form-urlencoded\r\n" +
        "Content-Length: ${body.length}$separator$body"

    @Test
    fun `a form post callback yields the code and the state`() {
        val result = parseFormPostCallback(request())

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "abc", state = "xyz"))
    }

    @Test
    fun `a bare LF separator is accepted too`() {
        // The spec says CRLF; real clients are not all so careful, and a callback lost to a
        // line-ending would look exactly like a user who never finished logging in.
        val result = parseFormPostCallback(request(separator = "\n\n"))

        assertThat(result).isInstanceOf(FormPostCallback.Success::class.java)
    }

    @Test
    fun `a GET is not a callback`() {
        // Browsers probe. A favicon fetch on this port must not be read as an authorization result.
        val result = parseFormPostCallback(request(method = "GET"))

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `another path on the same port is refused`() {
        val result = parseFormPostCallback(request(path = "/"))

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `a refusal is reported as such, with its reason`() {
        val body = "error=access_denied&error_description=User%20refused&state=xyz"

        val result = parseFormPostCallback(request(body = body))

        assertThat(result).isEqualTo(
            FormPostCallback.Failure(
                error = "access_denied",
                description = "User refused",
                state = "xyz",
            ),
        )
    }

    @Test
    fun `a body without a state is malformed, not a success`() {
        // State is the CSRF defence of this flow. A code without one is not usable.
        val result = parseFormPostCallback(request(body = "code=abc"))

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `an oversized request is refused rather than buffered`() {
        val result = parseFormPostCallback(request(body = "code=" + "a".repeat(100_000)))

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `the page handed back announces the outcome and a well formed response`() {
        val ok = callbackResponsePage(succeeded = true)

        assertThat(ok).startsWith("HTTP/1.1 200 OK")
        assertThat(ok).contains("Content-Length:")
        assertThat(callbackResponsePage(succeeded = false)).startsWith("HTTP/1.1 400")
    }
}
