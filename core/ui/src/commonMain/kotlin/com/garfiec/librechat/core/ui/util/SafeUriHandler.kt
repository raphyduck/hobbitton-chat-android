package com.garfiec.librechat.core.ui.util

import androidx.compose.ui.platform.UriHandler
import co.touchlab.kermit.Logger

/**
 * Schemes a link found in untrusted content may hand to the platform.
 *
 * `http`/`https` open a browser; `mailto` opens the mail composer and is kept because the agent
 * detail screen links a contact address with it. Everything else is refused: the platform's
 * default handler fires an `ACTION_VIEW` on whatever it is given, so a link in a reply, a citation,
 * a mission note or a server-provided authorization URL could otherwise reach the dialer, an app
 * store, another installed app's scheme or this app's own OAuth callback (review C5, 26/09/2026).
 */
private val OPENABLE_SCHEMES = setOf("http", "https", "mailto")

/**
 * Whether [uri] may be handed to the platform's opener: a syntactically valid scheme in
 * [OPENABLE_SCHEMES] (or [extraSchemes]), no control characters, and for `http`/`https` a real
 * authority. Matching is on the scheme alone, deliberately — a `javascript:`, `intent:`, `file:`,
 * `content:` or `tel:` link is refused whatever follows the colon.
 */
fun isSafeExternalUri(uri: String, extraSchemes: Set<String> = emptySet()): Boolean {
    val trimmed = uri.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isControl() }) return false
    val scheme = schemeOf(trimmed) ?: return false
    if (scheme !in OPENABLE_SCHEMES && scheme !in extraSchemes) return false
    if (scheme == "http" || scheme == "https") {
        val authorityStart = scheme.length + 3
        return trimmed.length > authorityStart &&
            trimmed.regionMatches(scheme.length, "://", 0, 3) &&
            trimmed[authorityStart] != '/'
    }
    return true
}

/** The lower-cased scheme of [uri] when it has a well-formed one (RFC 3986 §3.1), else null. */
fun schemeOf(uri: String): String? {
    val colon = uri.indexOf(':')
    if (colon <= 0) return null
    val scheme = uri.substring(0, colon)
    if (!scheme[0].isAsciiLetter()) return null
    if (!scheme.all { it.isAsciiLetter() || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) return null
    return scheme.lowercase()
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isControl(): Boolean = code < 0x20 || code == 0x7F

/**
 * The one [UriHandler] the app installs at its root, wrapping the platform's own.
 *
 * Every `LocalUriHandler.current` beneath the root resolves to this, so a link from a message, a
 * citation, a web-search card, a media list, a mission note, an agent contact or an MCP server's
 * authorization URL goes through the same gate: [isSafeExternalUri] decides, and a refused link
 * is logged by scheme only (the URL itself may carry a token) and otherwise ignored. A platform
 * failure to open an accepted link (no browser installed) is swallowed the same way rather than
 * crashing the screen that showed it.
 */
class SafeUriHandler(private val delegate: UriHandler) : UriHandler {

    override fun openUri(uri: String) {
        if (!isSafeExternalUri(uri)) {
            Logger.w { "SafeUriHandler: refused a link with scheme '${schemeOf(uri.trim()) ?: "?"}'" }
            return
        }
        runCatching { delegate.openUri(uri) }
            .onFailure { Logger.w(it) { "SafeUriHandler: the platform could not open the link" } }
    }
}
