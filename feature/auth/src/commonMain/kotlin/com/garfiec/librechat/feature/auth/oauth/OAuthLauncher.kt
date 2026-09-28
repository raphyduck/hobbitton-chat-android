package com.garfiec.librechat.feature.auth.oauth

/**
 * Platform-abstracted OAuth launcher.
 * Android: embedded portal web view for `openid` (see [embedsPortal]), Chrome Custom Tabs otherwise
 * iOS: ASWebAuthenticationSession
 */
interface OAuthLauncher {
    /** Open the OAuth flow for the given provider. */
    fun launchOAuth(provider: String, serverUrl: String)

    /** After OAuth redirect, extract refresh token from cookies/callback. */
    fun extractTokenFromCookies(serverUrl: String): String?

    /** Clear OAuth cookie/state after successful extraction. */
    fun clearOAuthCookie(serverUrl: String)

    /**
     * hobbitton (D-076): whether this platform can run the `openid` sign-in in an embedded web view
     * whose cookie jar [extractTokenFromCookies] reads. Only then can the login screen offer the
     * single sign-in (chat, then tasks, one portal session). A browser tab — Custom Tab or
     * `ASWebAuthenticationSession` — keeps its cookies to itself, so the refresh cookie it receives
     * never reaches the app.
     */
    val embedsPortal: Boolean get() = false
}

/**
 * Where LibreChat starts a provider's sign-in: `/oauth/<provider>`.
 *
 * Not `/api/oauth/…`: the server mounts its OAuth routes at `/oauth` (`app.use('/oauth', …)`,
 * v0.8.7) — the callback registered with the identity provider is `/oauth/openid/callback` too. The
 * `/api/oauth/…` path this client inherited reaches no OAuth route on that version.
 */
fun oauthEntryUrl(serverUrl: String, provider: String): String =
    "${serverUrl.trimEnd('/')}/oauth/$provider"
