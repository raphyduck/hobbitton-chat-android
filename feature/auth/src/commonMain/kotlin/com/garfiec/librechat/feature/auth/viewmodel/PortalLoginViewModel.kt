package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineAddressField
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.engine.EngineSignInResult
import com.garfiec.librechat.core.data.engine.validateEngineAddresses
import com.garfiec.librechat.core.data.portal.PortalNavigation
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.data.portal.classifyPortalNavigation
import com.garfiec.librechat.feature.auth.oauth.OAuthLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the sign-in is. */
enum class PortalLoginStep {
    /** Nothing in flight: the screen shows the addresses and the button. */
    Idle,

    /** The addresses are being saved and the round trip prepared; the web view is not up yet. */
    Preparing,

    /** The web view is on the portal — password, second factor, consent. */
    Portal,
}

/** Why the sign-in ended without a session — the only terms that change what to do next. */
enum class PortalLoginProblem {
    /** The addresses are not usable for a round trip (no portal, no scheduler to relay the code). */
    NOT_READY,

    /** The portal could not be reached. */
    UNREACHABLE,

    /** The portal refused, or granted less than the engine needs. */
    REFUSED,

    /** The round trip broke off midway (the relay, the code exchange). */
    INTERRUPTED,
}

@Immutable
data class PortalLoginUiState(
    /** This platform hosts the portal's web view and has the engine graph: the form shows. */
    val available: Boolean = false,
    val baseUrl: String = "",
    val issuerUrl: String = "",
    val schedulerUrl: String = "",
    /** The fields the last attempt refused, named so the messages stay in string resources. */
    val invalid: Set<EngineAddressField> = emptySet(),
    val step: PortalLoginStep = PortalLoginStep.Idle,
    /** What the web view shows; null while no round trip is open. */
    val page: String? = null,
    val problem: PortalLoginProblem? = null,
    /** The portal handed over its tokens: leave the sign-in screen. */
    val signedIn: Boolean = false,
)

/**
 * The only sign-in left (D-077): the portal, and nothing behind it.
 *
 * LibreChat is gone, and with it the `/oauth/openid` step, the refresh cookie read from the web
 * view's jar and the chat session opened with it. What remains is the round trip the Tasks tab
 * already ran — [PortalTasksSignIn]: PAR, PKCE, `state`, the code relayed by the scheduler, the
 * exchange, [com.garfiec.librechat.core.data.portal.PortalSession] — hosted in the app's own web
 * view (`PortalWebView`) so the portal's session cookie stays where the Tasks tab's re-sign-in
 * finds it.
 *
 * The screen asks first for the three addresses the engine graph runs on — engine, scheduler,
 * portal ([EngineSettingsStore]) — instead of a LibreChat server URL. All three are required here:
 * the scheduler is where the portal hands the code back.
 *
 * Both engine dependencies are null where the engine graph is absent (iOS, D-034): the form is then
 * not offered ([PortalLoginUiState.available]).
 */
class PortalLoginViewModel(
    private val settings: EngineSettingsStore?,
    private val tasks: PortalTasksSignIn?,
    oAuthLauncher: OAuthLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PortalLoginUiState(available = settings != null && tasks != null && oAuthLauncher.embedsPortal),
    )
    val state: StateFlow<PortalLoginUiState> = _state.asStateFlow()

    /** The web view was closed while the round trip was still being prepared. */
    private var abandoned = false

    init {
        val store = settings
        if (store != null) {
            viewModelScope.launch {
                runCatching {
                    Triple(store.baseUrl.first(), store.issuerUrl.first(), store.schedulerUrl.first())
                }.onSuccess { (base, issuer, scheduler) ->
                    // What was typed while the store was being read wins over what it held.
                    _state.update {
                        it.copy(
                            baseUrl = it.baseUrl.ifEmpty { base },
                            issuerUrl = it.issuerUrl.ifEmpty { issuer },
                            schedulerUrl = it.schedulerUrl.ifEmpty { scheduler },
                        )
                    }
                }.onFailure { Logger.w(it) { "Could not read the stored addresses" } }
            }
        }
    }

    fun onBaseUrl(value: String) = edit(EngineAddressField.BASE_URL) { it.copy(baseUrl = value) }

    fun onIssuerUrl(value: String) = edit(EngineAddressField.ISSUER_URL) { it.copy(issuerUrl = value) }

    fun onSchedulerUrl(value: String) = edit(EngineAddressField.SCHEDULER_URL) { it.copy(schedulerUrl = value) }

    /** Clears the field's own complaint as it is edited; keeping it would blame a fixed field. */
    private fun edit(field: EngineAddressField, change: (PortalLoginUiState) -> PortalLoginUiState) {
        _state.update { current -> change(current).copy(invalid = current.invalid - field, problem = null) }
    }

    /** Saves the addresses and opens the portal. Ignored while a round trip is already open. */
    fun start() {
        val current = _state.value
        val store = settings ?: return
        val handoff = tasks ?: return
        if (current.step != PortalLoginStep.Idle) return
        val invalid = validateEngineAddresses(
            baseUrl = current.baseUrl,
            issuerUrl = current.issuerUrl,
            schedulerUrl = current.schedulerUrl,
            schedulerRequired = true,
        )
        if (invalid.isNotEmpty()) {
            _state.update { it.copy(invalid = invalid) }
            return
        }
        abandoned = false
        _state.update { it.copy(step = PortalLoginStep.Preparing, problem = null, invalid = emptySet()) }
        viewModelScope.launch {
            val prepared = try {
                store.save(baseUrl = current.baseUrl, issuerUrl = current.issuerUrl, schedulerUrl = current.schedulerUrl)
                handoff.isReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Logger.w(e) { "Could not save the addresses for the sign-in" }
                false
            }
            when {
                abandoned -> _state.update { it.copy(step = PortalLoginStep.Idle, page = null) }
                !prepared -> fail(PortalLoginProblem.NOT_READY)
                else -> runRoundTrip(handoff)
            }
        }
    }

    private suspend fun runRoundTrip(handoff: PortalTasksSignIn) {
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
        _state.update { it.copy(step = PortalLoginStep.Portal) }
        handoff.start { url -> _state.update { it.copy(page = url) } }
        val outcome = handoff.progress.first { it is EngineSignInProgress.Termine } as EngineSignInProgress.Termine
        handoff.acknowledge()
        Logger.i("Portal") { "Sign-in: ${outcome.issue::class.simpleName}" }
        settle(outcome.issue)
    }

    private fun settle(result: EngineSignInResult) {
        when (result) {
            EngineSignInResult.Authorized ->
                _state.update { it.copy(step = PortalLoginStep.Idle, page = null, signedIn = true) }
            EngineSignInResult.Cancelled ->
                _state.update { it.copy(step = PortalLoginStep.Idle, page = null) }
            else -> fail(problemOf(result))
        }
    }

    /**
     * Offered every main-frame navigation of the web view, before it loads. True: handled, the web
     * view must not go there. Called on the main thread; idempotent.
     */
    fun onNavigation(url: String): Boolean = when (classifyPortalNavigation(url)) {
        PortalNavigation.Load -> false
        PortalNavigation.Refused -> true
        PortalNavigation.AppCallback -> {
            tasks?.offer(url)
            true
        }
    }

    /** The web view was closed: the round trip stops now rather than after its five minutes. */
    fun cancel() {
        when (_state.value.step) {
            PortalLoginStep.Idle -> Unit
            PortalLoginStep.Preparing -> abandoned = true
            // The round trip publishes `Cancelled`; the wait in [runRoundTrip] then settles.
            PortalLoginStep.Portal -> tasks?.cancel()
        }
    }

    private fun fail(problem: PortalLoginProblem) {
        _state.update { it.copy(step = PortalLoginStep.Idle, page = null, problem = problem) }
    }
}

/** What the screen says about a round trip that ended without tokens. Pure, so it is tested. */
internal fun problemOf(result: EngineSignInResult): PortalLoginProblem = when (result) {
    EngineSignInResult.NotConfigured, EngineSignInResult.NoCallbackHost -> PortalLoginProblem.NOT_READY
    is EngineSignInResult.PortalUnreachable -> PortalLoginProblem.UNREACHABLE
    is EngineSignInResult.Refused, is EngineSignInResult.MissingAuthorizationScope -> PortalLoginProblem.REFUSED
    is EngineSignInResult.Interrupted -> PortalLoginProblem.INTERRUPTED
    // Not failures; listed so a new outcome has to be placed here on purpose.
    EngineSignInResult.Authorized, EngineSignInResult.Cancelled -> PortalLoginProblem.INTERRUPTED
}
