package com.garfiec.librechat.feature.chat.util

/**
 * Whether [url] and [baseUrl] share an origin — scheme, host and port, with the default port of
 * each scheme applied.
 *
 * Used to decide which image loader shows an `image_url` content part (review C7, 26/09/2026):
 * only a URL under the server's own origin goes through the authenticated loader. Same host on
 * another scheme or port is *not* the server — the bearer must not travel there.
 */
fun isSameOrigin(url: String, baseUrl: String): Boolean {
    val a = originOf(url) ?: return false
    val b = originOf(baseUrl) ?: return false
    return a == b
}

/** `scheme://host:port` of an `http`/`https` URL, lower-cased and with the default port made explicit; null otherwise. */
internal fun originOf(url: String): String? {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    val defaultPort = when (scheme) {
        "http" -> HTTP_PORT
        "https" -> HTTPS_PORT
        else -> return null
    }
    val authorityStart = schemeEnd + 3
    val authorityEnd = trimmed.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
        .let { if (it < 0) trimmed.length else it }
    val authority = trimmed.substring(authorityStart, authorityEnd).substringAfterLast('@')
    if (authority.isEmpty()) return null
    val (host, port) = splitHostPort(authority) ?: return null
    return "$scheme://${host.lowercase()}:${port ?: defaultPort}"
}

private fun splitHostPort(authority: String): Pair<String, Int?>? {
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        if (close < 0) return null
        val host = authority.substring(0, close + 1)
        val rest = authority.substring(close + 1)
        return when {
            rest.isEmpty() -> host to null
            rest.startsWith(':') -> rest.drop(1).toIntOrNull()?.let { host to it }
            else -> null
        }
    }
    val colon = authority.lastIndexOf(':')
    if (colon < 0) return authority to null
    val host = authority.substring(0, colon)
    val port = authority.substring(colon + 1).toIntOrNull() ?: return null
    return if (host.isEmpty()) null else host to port
}

private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
