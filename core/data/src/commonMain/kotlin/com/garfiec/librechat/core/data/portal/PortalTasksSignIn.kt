package com.garfiec.librechat.core.data.portal

import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.auth.CALLBACK_SCHEME
import com.garfiec.librechat.core.network.engine.auth.callbackRedirectUri
import kotlinx.coroutines.flow.StateFlow

/**
 * The tasks half of the single sign-in (D-076), as a screen hosting the portal's web view sees it.
 *
 * Not a second implementation: the portal round trip is still [EngineSignInLauncher] — PAR, PKCE,
 * `state`, the code exchange, [PortalSession] — exactly as the Tasks tab ran it. What changes is
 * where its page opens. The launcher is handed a callback that loads the authorization URL **in the
 * web view that just went through the portal**, so the portal's session cookie is already there and
 * the only thing left to do is the consent click; and the page's final hop to the app scheme is
 * caught by that web view ([offer]) and dropped into the same mailbox the deep link fills.
 *
 * Bound by `engineModule` (Android); resolved with `getOrNull()` by the login screen, so on a
 * platform without the engine graph the login simply stops after the chat.
 */
class PortalTasksSignIn(
    private val access: suspend () -> EngineAccess?,
    private val launcher: EngineSignInLauncher,
    private val delivery: EngineCallbackDelivery,
) {
    /** Where the round trip is — the same flow the Tasks tab watches. */
    val progress: StateFlow<EngineSignInProgress> get() = launcher.etat

    /**
     * Whether the tasks can be signed into at all: an engine, a portal, and a scheduler address
     * for the code to come back to. When not, the login ends with the chat — the Tasks tab says
     * what is missing, where it can be fixed.
     */
    suspend fun isReady(): Boolean {
        val engine = access() ?: return false
        return engine.isConfigured && callbackRedirectUri(engine.schedulerUrl) != null
    }

    /** Starts the round trip; [showPage] receives the authorization URL to load in the web view. */
    fun start(showPage: (url: String) -> Unit) = launcher.lancer(showPage)

    /** The web view was closed: the round trip stops now rather than after its five minutes. */
    fun cancel() = launcher.annuler()

    /** The outcome has been read; the next round starts clean. */
    fun acknowledge() = launcher.acquitter()

    /**
     * Offers a navigation of the web view. True when it was the portal's return to the app —
     * handed to the mailbox, and not to be loaded: the web view has no idea what to do with the
     * app's scheme, and would show an error page with the authorization code in its address.
     */
    fun offer(url: String): Boolean {
        if (!isPortalCallback(url)) return false
        delivery.deposer(url)
        return true
    }
}

/** `at.hobbitton.chat:…` — the scheme the scheduler's page bounces the code to. */
fun isPortalCallback(url: String): Boolean = url.startsWith("$CALLBACK_SCHEME:", ignoreCase = true)
