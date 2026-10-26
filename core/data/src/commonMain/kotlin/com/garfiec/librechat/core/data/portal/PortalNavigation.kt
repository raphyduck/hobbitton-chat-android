package com.garfiec.librechat.core.data.portal

import com.garfiec.librechat.core.common.network.CleartextPolicy

/**
 * What to do with one navigation of the portal's web view, during the sign-in (D-076, D-077).
 *
 * Since D-077 the sign-in is the portal's round trip and nothing else: the web view loads the
 * authorization URL, the person signs in (password, second factor, consent), the portal posts the
 * code to the scheduler, and the scheduler's page bounces it to the app scheme. There is no
 * LibreChat step any more — no `/oauth/openid`, no refresh cookie to read, no chat origin to stop
 * on.
 */
sealed interface PortalNavigation {
    /** An ordinary page of the round trip — the portal, the scheduler's relay. Load it. */
    data object Load : PortalNavigation

    /** The scheduler's page bouncing the authorization code to the app scheme. */
    data object AppCallback : PortalNavigation

    /** Not `http(s)`, and not the app's callback: never loaded. */
    data object Refused : PortalNavigation
}

/**
 * Classifies [url], one main-frame navigation of the sign-in's web view. `http://` loads only
 * towards a private host (26/10/2026, the same rule as the clients' cleartext guard): a portal
 * that redirected in the clear to a public host would otherwise be followed.
 */
fun classifyPortalNavigation(url: String): PortalNavigation = when {
    isPortalCallback(url) -> PortalNavigation.AppCallback
    url.startsWith("https://", ignoreCase = true) -> PortalNavigation.Load
    url.startsWith("http://", ignoreCase = true) && CleartextPolicy.isPermitted(url) -> PortalNavigation.Load
    else -> PortalNavigation.Refused
}
