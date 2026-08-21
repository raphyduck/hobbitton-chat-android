package com.garfiec.librechat.core.network.engine.auth

import io.ktor.http.parseUrlEncodedParameters

/**
 * What came back from Authelia on the loopback socket.
 *
 * The authorization server answers with an HTML page whose form auto-POSTs to the redirect URI, so
 * what arrives is a real HTTP request with a urlencoded body — not a URL with a query string. That
 * is the whole reason this app opens a socket instead of registering `at.hobbitton.chat://…`: a
 * form POST cannot be delivered to an app scheme, and an App Link intent would drop the body.
 */
sealed interface FormPostCallback {
    data class Success(val code: String, val state: String) : FormPostCallback

    /** The server said no — expired request, refused consent, second factor abandoned. */
    data class Failure(val error: String, val description: String?, val state: String?) :
        FormPostCallback

    /** Something reached the socket that was not the callback at all. */
    data class Malformed(val reason: String) : FormPostCallback
}

private const val MAX_BODY_BYTES = 64 * 1024

/**
 * Parses one raw HTTP request off the loopback socket.
 *
 * Written against the raw text rather than an HTTP server library because the whole server is one
 * socket, alive for one request. What it must get right is narrow and worth stating:
 *
 *  * only `POST` is the callback; a `GET` on this port is a browser probe, a favicon fetch, or
 *    someone poking around, and must not be read as an authorization result;
 *  * the body is separated by a blank line, CRLF by spec but LF in practice from some clients;
 *  * anything oversized is refused rather than buffered — this socket is open to any process on
 *    the device.
 */
fun parseFormPostCallback(raw: String, expectedPath: String = "/oauth/authelia"): FormPostCallback {
    if (raw.length > MAX_BODY_BYTES) return FormPostCallback.Malformed("request too large")

    val requestLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
    val parts = requestLine.split(' ')
    if (parts.size < 2) return FormPostCallback.Malformed("no request line")

    val (method, target) = parts[0] to parts[1]
    if (!method.equals("POST", ignoreCase = true)) {
        return FormPostCallback.Malformed("method $method is not the callback")
    }
    if (target.substringBefore('?') != expectedPath) {
        return FormPostCallback.Malformed("unexpected path $target")
    }

    val separator = listOf("\r\n\r\n", "\n\n").firstOrNull { raw.contains(it) }
        ?: return FormPostCallback.Malformed("no body")
    val body = raw.substringAfter(separator)
    val fields = body.parseUrlEncodedParameters()

    fields["error"]?.let { error ->
        return FormPostCallback.Failure(
            error = error,
            description = fields["error_description"],
            state = fields["state"],
        )
    }

    val code = fields["code"] ?: return FormPostCallback.Malformed("no code in body")
    val state = fields["state"] ?: return FormPostCallback.Malformed("no state in body")
    return FormPostCallback.Success(code = code, state = state)
}

/**
 * The page the browser is left on. Plain, self-contained, and it says the one thing the person
 * needs to know: this tab is finished with.
 */
fun callbackResponsePage(succeeded: Boolean): String {
    val title = if (succeeded) "Connexion réussie" else "Connexion interrompue"
    val message = if (succeeded) {
        "Vous pouvez revenir à l'application et fermer cet onglet."
    } else {
        "L'application n'a pas reçu d'autorisation. Réessayez depuis l'application."
    }
    val body = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title></head>
        <body style="font-family:system-ui,sans-serif;margin:3rem auto;max-width:32rem;padding:0 1rem">
        <h1 style="font-size:1.25rem">$title</h1><p>$message</p></body></html>
    """.trimIndent()
    val status = if (succeeded) "200 OK" else "400 Bad Request"
    return buildString {
        append("HTTP/1.1 $status\r\n")
        append("Content-Type: text/html; charset=utf-8\r\n")
        append("Content-Length: ${body.encodeToByteArray().size}\r\n")
        append("Connection: close\r\n\r\n")
        append(body)
    }
}
