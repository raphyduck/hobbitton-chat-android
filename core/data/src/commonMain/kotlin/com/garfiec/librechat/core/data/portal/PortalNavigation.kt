package com.garfiec.librechat.core.data.portal

import io.ktor.http.Url

/**
 * What to do with one navigation of the portal's web view, during the single sign-in (D-076).
 *
 * The path the chat's half takes, on a deployment like the one this fork targets:
 *
 * 1. `https://chat/oauth/openid` — the app loads it. The edge sends it to the portal first
 *    (forward-auth), the person signs in there — password and second factor — and comes back.
 * 2. LibreChat redirects to the portal's authorization endpoint; the portal, already satisfied,
 *    redirects to `https://chat/oauth/openid/callback?code=…`.
 * 3. LibreChat sets its `refreshToken` cookie on that response and redirects **out of `/oauth`**,
 *    to the web client (`/`) — or to `/login` when the sign-in failed.
 *
 * Step 3's hop is [ChatReturned]: stop there, read the cookie. Loading the web client would be
 * worse than useless — on start it spends that very refresh token on its own `/api/auth/refresh`,
 * and the server rotates it under the app's feet.
 */
sealed interface PortalNavigation {
    /** An ordinary page of the round trip — the portal, `/oauth/…` on the chat. Load it. */
    data object Load : PortalNavigation

    /** Back on the chat server outside `/oauth`: the chat's half is over, the cookie is set (or not). */
    data object ChatReturned : PortalNavigation

    /** The scheduler's page bouncing the tasks' authorization code to the app scheme. */
    data object AppCallback : PortalNavigation

    /** Not `http(s)`, and not the app's callback: never loaded. */
    data object Refused : PortalNavigation
}

/** Classifies [url] against the chat server being signed into, [chatBaseUrl]. */
fun classifyPortalNavigation(url: String, chatBaseUrl: String): PortalNavigation {
    if (isPortalCallback(url)) return PortalNavigation.AppCallback
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
        return PortalNavigation.Refused
    }
    val target = runCatching { Url(url) }.getOrNull() ?: return PortalNavigation.Refused
    val chat = runCatching { Url(chatBaseUrl.trim()) }.getOrNull() ?: return PortalNavigation.Load
    val sameOrigin = target.host.equals(chat.host, ignoreCase = true) &&
        target.protocol.name.equals(chat.protocol.name, ignoreCase = true) &&
        target.port == chat.port
    if (!sameOrigin) return PortalNavigation.Load

    // Relative to the server's own base path, so a LibreChat served under a prefix still works.
    val base = chat.encodedPath.trimEnd('/')
    val path = target.encodedPath.removePrefix(base)
    val inOAuth = path == "/oauth" || path.startsWith("/oauth/")
    return if (inOAuth) PortalNavigation.Load else PortalNavigation.ChatReturned
}
