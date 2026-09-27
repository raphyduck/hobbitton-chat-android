package com.garfiec.librechat.core.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The web view the single sign-in runs in (D-076): the portal's login, then its consent page.
 *
 * Here because **two features host it** — the login screen (`feature/auth`), which signs into the
 * chat then the tasks, and the Tasks tab (`feature/tasks`), which signs in again when the portal's
 * token has lapsed — and they cannot see each other. One web view, one cookie jar: whatever the
 * portal's session cookie opened in one is open in the other, which is the whole point.
 *
 * It knows nothing of chats, engines or schemes. It shows [url], and offers every main-frame
 * navigation — redirects included — to [onNavigation] **before** loading it; `true` means the
 * caller handled it and the web view must not go there. Anything that is not `http(s)` is never
 * loaded, handled or not: an `intent:` or custom scheme opened by a page is not this view's to
 * follow. A TLS error cancels the load; it is never proceeded past.
 *
 * Unlike the content web views of the chat (`LockedDownWebView`), this one needs JavaScript, DOM
 * storage and the process cookie jar — the portal is a single-page application, and the cookie is
 * the product. What it gets in exchange is a narrow job: it only ever shows pages the app itself
 * asked for, from the chat server and the portal.
 *
 * Android only in practice: on iOS the engine graph is absent (D-034) and nothing shows it.
 *
 * @param url the page to show. A new value loads it; the same value again does not reload.
 * @param onNavigation offered each main-frame navigation first; `true`: handled, do not load.
 */
@Composable
expect fun PortalWebView(
    url: String?,
    onNavigation: (url: String) -> Boolean,
    modifier: Modifier = Modifier,
)

/** Whether a navigation may be loaded by the web view at all: `http` and `https`, nothing else. */
fun isWebNavigation(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
