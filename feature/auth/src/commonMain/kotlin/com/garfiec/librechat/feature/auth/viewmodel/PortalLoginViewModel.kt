package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.portal.PortalNavigation
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.data.portal.classifyPortalNavigation
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.feature.auth.oauth.OAuthLauncher
import com.garfiec.librechat.feature.auth.oauth.oauthEntryUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the single sign-in is. */
enum class PortalLoginStep {
    /** Nothing in flight: the login screen shows its form. */
    Idle,

    /** The web view is on the portal — password, second factor — or on LibreChat's OAuth hops. */
    Portal,

    /** Back from the portal: reading the chat's refresh cookie and opening the session. */
    Session,

    /** Chat signed in; the same web view is on the portal's consent page for the tasks. */
    Tasks,
}

/** Why the chat's half ended without a session — the only terms that change what to do next. */
enum class PortalLoginProblem {
    /** Back on the chat with no refresh cookie: the server sent the person to its login page. */
    NO_SESSION,

    /** A refresh cookie was there, and the server would not open a session with it. */
    SESSION_FAILED,
}

@Immutable
data class PortalLoginUiState(
    /** The server offers `openid` and this platform hosts the portal: the button shows. */
    val offered: Boolean = false,
    /** The server's own label for that button (`OPENID_BUTTON_LABEL`), when it sets one. */
    val label: String? = null,
    val step: PortalLoginStep = PortalLoginStep.Idle,
    /** What the web view shows; null while no round trip is open. */
    val page: String? = null,
    val problem: PortalLoginProblem? = null,
    /** Server text safe to render (`Result.Error.message`), next to [problem]. */
    val problemDetail: String? = null,
    /** The chat session is open and the tasks step is over, or was skipped: leave the login screen. */
    val signedIn: Boolean = false,
)

/**
 * The single sign-in (D-076): one web view, one password, one second factor, one consent click —
 * and at the end, a chat session *and* the tasks' portal token.
 *
 * 1. **Chat.** The web view loads `<server>/oauth/openid`. The edge sends it through the portal;
 *    LibreChat completes its OpenID sign-in and sets its `refreshToken` cookie. The navigation back
 *    out of `/oauth` is caught ([classifyPortalNavigation]), the cookie is read from the web view's
 *    jar, and handed to the path every other sign-in ends on — [AuthRepository.loginWithOAuthToken]:
 *    store the refresh token, refresh, load the user, establish the account.
 * 2. **Tasks.** In the **same** web view — same jar, so the portal's session cookie is there — the
 *    existing portal round trip runs ([PortalTasksSignIn]: PAR, PKCE, `state`, code exchange,
 *    [com.garfiec.librechat.core.data.portal.PortalSession]). The portal asks nothing but the
 *    consent it is obliged to ask for `authelia.bearer.authz`.
 *
 * The chat session is opened **before** the consent step, and the screen is only left after it:
 * the refresh token spends as little time as possible in memory, a refusal from the chat server is
 * reported before anyone is asked to consent to anything, and the navigation to the chat waits
 * for the web view to be done. A tasks step that fails or is closed does not undo the chat: the
 * Tasks tab says what is missing and signs in again from there.
 *
 * Why a web view and not the upstream Custom Tab: a browser tab keeps its cookies to itself, so
 * the refresh cookie never reaches [OAuthLauncher.extractTokenFromCookies]; and the portal session
 * it opens is the browser's, not the app's, so the tasks would ask for the password again. The
 * portal is this deployment's own — RFC 8252's objection to embedded views concerns third-party
 * identity providers.
 */
class PortalLoginViewModel(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
    private val oAuthLauncher: OAuthLauncher,
    private val serverDataStore: ServerDataStore,
    private val accountSwitcher: AccountSwitcher,
    /** Null where the engine graph is absent (iOS, D-034): the sign-in then ends with the chat. */
    private val tasks: PortalTasksSignIn?,
) : ViewModel() {

    private val _state = MutableStateFlow(PortalLoginUiState())
    val state: StateFlow<PortalLoginUiState> = _state.asStateFlow()

    /** The chat server of the round trip in flight — what navigations are judged against. */
    private var chatBase: String? = null

    init {
        // Same source as LoginViewModel: in an add-account flow the pending server's config.
        val configSource = accountSwitcher.pendingAdd?.startupConfig ?: configRepository.startupConfig
        viewModelScope.launch {
            configSource.collect { config ->
                if (config == null) return@collect
                val offersOpenId = config.socialLoginEnabled &&
                    config.socialLogins.orEmpty().any { it.equals(OPENID, ignoreCase = true) }
                _state.update {
                    it.copy(
                        offered = oAuthLauncher.embedsPortal && offersOpenId,
                        label = config.openidLabel?.takeIf { label -> label.isNotBlank() },
                    )
                }
            }
        }
    }

    private fun signInServerUrl(): String =
        (accountSwitcher.pendingAdd?.serverUrl ?: serverDataStore.getBaseUrl()).trim().trimEnd('/')

    /** Opens the portal in the web view. Ignored while a round trip is already open. */
    fun start() {
        if (_state.value.step != PortalLoginStep.Idle) return
        val serverUrl = signInServerUrl()
        if (serverUrl.isBlank()) return
        chatBase = serverUrl
        // A refresh cookie left by an earlier round trip must not be read as this one's — in an
        // add-account flow it could belong to another account on the same server.
        oAuthLauncher.clearOAuthCookie(serverUrl)
        _state.update {
            it.copy(
                step = PortalLoginStep.Portal,
                page = oauthEntryUrl(serverUrl, OPENID),
                problem = null,
                problemDetail = null,
            )
        }
    }

    /**
     * Offered every main-frame navigation of the web view, before it loads. True: handled, the web
     * view must not go there. Called on the main thread; idempotent, since a navigation can be
     * offered twice (redirect, then page start).
     */
    fun onNavigation(url: String): Boolean {
        val base = chatBase ?: return false
        return when (classifyPortalNavigation(url, base)) {
            PortalNavigation.Load -> false
            PortalNavigation.Refused -> true
            PortalNavigation.AppCallback -> {
                tasks?.offer(url)
                true
            }
            PortalNavigation.ChatReturned -> {
                if (_state.value.step == PortalLoginStep.Portal) openChatSession(base)
                // Never loaded, in any step: the web client would spend the refresh token itself.
                true
            }
        }
    }

    /**
     * The web view was closed. Before the chat is signed in, that abandons the sign-in; during the
     * consent step, it only skips the tasks — the chat session is already open.
     */
    fun cancel() {
        when (_state.value.step) {
            PortalLoginStep.Idle, PortalLoginStep.Session -> Unit
            PortalLoginStep.Portal -> {
                chatBase?.let(oAuthLauncher::clearOAuthCookie)
                chatBase = null
                _state.update { it.copy(step = PortalLoginStep.Idle, page = null) }
            }
            // The round trip publishes `Cancelled`; the step's own wait then finishes the sign-in.
            PortalLoginStep.Tasks -> tasks?.cancel()
        }
    }

    private fun openChatSession(base: String) {
        _state.update { it.copy(step = PortalLoginStep.Session) }
        viewModelScope.launch {
            val refreshToken = awaitRefreshCookie(base)
            // Read once, then gone from the jar: the web view goes on to other hosts, and nothing
            // else in the app should ever find it there.
            oAuthLauncher.clearOAuthCookie(base)
            if (refreshToken == null) {
                fail(PortalLoginProblem.NO_SESSION, detail = null)
                return@launch
            }
            when (val result = authRepository.loginWithOAuthToken(refreshToken)) {
                is Result.Success -> signInTasks()
                is Result.Error -> fail(PortalLoginProblem.SESSION_FAILED, detail = result.message)
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * The cookie is set by the callback's response, and the jar may publish it a moment after the
     * redirect that follows is reported. A few short reads rather than one.
     */
    private suspend fun awaitRefreshCookie(base: String): String? {
        repeat(COOKIE_READS) { attempt ->
            oAuthLauncher.extractTokenFromCookies(base)?.let { return it }
            if (attempt < COOKIE_READS - 1) delay(COOKIE_READ_INTERVAL_MS)
        }
        return null
    }

    private suspend fun signInTasks() {
        val handoff = tasks
        if (handoff == null || !isReady(handoff)) {
            finish()
            return
        }
        // A round trip the Tasks tab left behind would turn `start` into a no-op, or have its stale
        // outcome read as this one's.
        when (handoff.progress.value) {
            EngineSignInProgress.EnCours -> {
                handoff.cancel()
                handoff.acknowledge()
            }
            is EngineSignInProgress.Termine -> handoff.acknowledge()
            EngineSignInProgress.Idle -> Unit
        }
        _state.update { it.copy(step = PortalLoginStep.Tasks) }
        handoff.start { url -> _state.update { it.copy(page = url) } }
        val outcome = handoff.progress.first { it is EngineSignInProgress.Termine }
        Logger.i("Portal") { "Tasks sign-in after the chat's: $outcome" }
        handoff.acknowledge()
        finish()
    }

    private suspend fun isReady(handoff: PortalTasksSignIn): Boolean = try {
        handoff.isReady()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Logger.w(e) { "Could not read the engine settings; the sign-in ends with the chat" }
        false
    }

    private fun finish() {
        chatBase = null
        _state.update { it.copy(step = PortalLoginStep.Idle, page = null, signedIn = true) }
    }

    private fun fail(problem: PortalLoginProblem, detail: String?) {
        chatBase = null
        _state.update {
            it.copy(step = PortalLoginStep.Idle, page = null, problem = problem, problemDetail = detail)
        }
    }

    private companion object {
        const val OPENID = "openid"
        const val COOKIE_READS = 5
        const val COOKIE_READ_INTERVAL_MS = 200L
    }
}
