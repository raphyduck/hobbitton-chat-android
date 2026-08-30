package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.engine.EngineSignInResult
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Tasks tab shows.
 *
 * [engineConfigured] is separate from [error] on purpose: « you have not set the engine up » and
 * « the engine did not answer » are different problems with different remedies, and collapsing them
 * into one red banner sends the person to check their network when they should be filling in a
 * settings form.
 */
data class TasksUiState(
    val engineConfigured: Boolean = true,
    val loading: Boolean = false,
    val missions: List<Mission> = emptyList(),
    /**
     * The recurring missions, from the scheduler — a different service from the engine, and the
     * only place that knows a mission runs every night. Empty when no scheduler is configured,
     * which is a normal state and not an error.
     */
    val scheduled: List<ScheduledMission> = emptyList(),
    val schedulerConfigured: Boolean = false,
    /**
     * What the platform spent, by model, over the last week. Null while unknown — either the
     * scheduler is not configured, or its own gateway did not answer. Null is rendered as nothing
     * at all rather than as zero: a « 0,00 $ » on a screen about money is a claim, and one that
     * would be false here.
     */
    val consumption: Consumption? = null,
    /**
     * Which providers answer — null until someone asks, and deliberately so.
     *
     * Obtaining it calls every model for real (~0,0015 $, two to three seconds). Loading it with
     * the rest of the screen would spend money on every glance at the tab, for an answer that
     * changes about once a month. It stays null until [checkProviders].
     */
    val providers: ProviderHealth? = null,
    val providersChecking: Boolean = false,
    /** Why the last provider check failed, or null. Kept apart from [error] for the usual reason:
     * a gateway that did not answer is not an engine that did not answer. */
    val providersError: String? = null,
    /** Why the last call failed, or null. The screen turns it into a sentence and an offer. */
    val error: EngineFailureKind? = null,
    /**
     * The models a new mission may be launched on, and the one to tick when the sheet opens.
     *
     * Loaded when the sheet is opened, not with the rest of the tab: the catalogue is 11,8 kB and
     * changes about once a month, so paying for it on every pull-to-refresh buys nothing. Empty
     * until then, and empty is a valid state — the sheet simply offers no choice and the mission
     * runs on the profile's own model, exactly as it did before this existed.
     */
    val models: List<EngineSelectableModel> = emptyList(),
    /** The connectors this deployment offers — fetched from the scheduler, never a local copy. */
    val catalogue: ConnectorCatalogue = ConnectorCatalogue(),
    /** The catalogue would not load: the sheet says so rather than offering an empty list. */
    val connectorsFailed: Boolean = false,
    val preselectedModel: EngineSelectableModel? = null,
    /** The portal round trip is in flight: the browser is open, the person is proving who they are. */
    val signingIn: Boolean = false,
    /**
     * Why the last sign-in did not end in a token, or null.
     *
     * Kept apart from [error] because they answer different questions. [error] says the engine
     * turned a request away; this says the *portal* did — and the remedies do not overlap.
     */
    val signInProblem: EngineSignInProblem? = null,
)

/** What a failed sign-in means, in the only terms that change what the person does next. */
enum class EngineSignInProblem {
    /** No engine or portal address stored: there is nothing to sign in to yet. */
    NOT_CONFIGURED,

    /**
     * L'adresse du planificateur manque, et c'est par là que le code revient.
     *
     * Distingué de [NOT_CONFIGURED] parce que la réparation l'est : il ne manque pas « les
     * réglages », il manque CE réglage-là. Facultatif pour tout le reste de l'onglet, il est la
     * condition de la connexion depuis que la route de retour vit sur le planificateur.
     */
    NO_CALLBACK_HOST,

    /** The portal never answered, or answered something unusable. Retrying is reasonable. */
    PORTAL_UNREACHABLE,

    /** The portal said no: consent declined, second factor abandoned, request expired. */
    REFUSED,

    /** It broke somewhere it should not have — including the five-minute deadline running out. */
    INTERRUPTED,

    /**
     * Signed in, and the token still will not open the engine — the client is not allowed to ask
     * for `authelia.bearer.authz`. Its own case because it is the one failure that looks like a
     * success, and the only one the person cannot fix from the phone.
     */
    MISSING_SCOPE,
}

class TasksViewModel(
    private val repository: EngineMissionRepository,
    private val scheduler: SchedulerRepository,
    private val settings: EngineSettingsStore,
    private val portal: EngineSignInLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        refresh()
        followPortal()
    }

    /**
     * Sends the person through the portal, and reloads once a token exists.
     *
     * [openBrowser] comes from the screen — Compose's `UriHandler` — rather than from this class:
     * a view model that opens browsers is a view model that cannot be tested without one.
     *
     * This is the half of the flow that was missing until 24 August. Everything under it had been
     * written and unit-tested; nothing called it, so the tab could only ever report a failed
     * sign-in, and no amount of re-entering the password changed that.
     */
    fun signIn(openBrowser: (url: String) -> Unit) = portal.lancer(openBrowser)

    /**
     * Follows the portal round trip rather than awaiting it.
     *
     * The wait used to live in `viewModelScope` — which dies with the screen, and the screen is
     * exactly what goes away when the browser comes to the front. The round trip now lives on the
     * application scope; this screen watches it, and finds the outcome waiting even if it was
     * destroyed in between.
     */
    private fun followPortal() {
        viewModelScope.launch {
            portal.etat.collect { progress ->
                when (progress) {
                    EngineSignInProgress.Idle -> _state.update { it.copy(signingIn = false) }
                    EngineSignInProgress.EnCours ->
                        _state.update { it.copy(signingIn = true, signInProblem = null) }
                    is EngineSignInProgress.Termine -> {
                        _state.update {
                            it.copy(signingIn = false, signInProblem = progress.issue.asProblem())
                        }
                        if (progress.issue is EngineSignInResult.Authorized) refresh()
                        // Acknowledged so a second attempt starts clean — otherwise a recreated
                        // screen's `collect` would replay the old outcome as if it had just landed.
                        portal.acquitter()
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (settings.access() == null) {
                // Not an error: nothing is broken, the engine simply has not been configured. The
                // screen offers the settings form rather than a retry button that cannot help.
                _state.update { it.copy(engineConfigured = false, loading = false, error = null) }
                return@launch
            }
            _state.update { it.copy(engineConfigured = true, loading = true, error = null) }
            refreshScheduled()
            runCatching { repository.missions() to repository.profiles() }
                .onSuccess { (missions, profiles) ->
                    profils = profiles
                    _state.update {
                        it.copy(
                            loading = false,
                            // Newest first: the mission someone just launched is the one they are
                            // looking for, and the engine returns them oldest first.
                            missions = missions.sortedByDescending { mission -> mission.createdAtMillis ?: 0 },
                        )
                    }
                }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not read the engine's missions" }
                    _state.update { it.copy(loading = false, error = failure.engineFailureKind()) }
                }
        }
    }

    /**
     * The scheduler in its own request, and its failures kept off the engine's error banner.
     *
     * The two services are independent: a scheduler that is down must not empty the sessions list,
     * and an engine that is down must not hide tonight's schedule. Collapsing them into one
     * `runCatching` would make either outage look like both.
     */
    private suspend fun refreshScheduled() {
        runCatching { scheduler.isConfigured() to scheduler.missions() }
            .onSuccess { (configured, missions) ->
                _state.update { it.copy(schedulerConfigured = configured, scheduled = missions) }
            }
            .onFailure { failure ->
                Logger.w(failure, tag = "Tasks") { "Could not read the scheduler" }
                _state.update { it.copy(scheduled = emptyList()) }
            }
        refreshConsumption()
    }

    /**
     * The week's spend, in its own request again — same reasoning one level down.
     *
     * This one reaches further than the others: the scheduler asks the gateway, which may be down
     * while the scheduler is perfectly fine. A failure here must therefore not empty the mission
     * list, so it clears only its own field and says nothing on the banner.
     */
    private suspend fun refreshConsumption() {
        runCatching { scheduler.consumption(days = CONSUMPTION_DAYS) }
            .onSuccess { report -> _state.update { it.copy(consumption = report) } }
            .onFailure { failure ->
                Logger.w(failure, tag = "Tasks") { "Could not read the week's spend" }
                _state.update { it.copy(consumption = null) }
            }
    }

    /**
     * Asks every provider whether it still answers. **Spends money**, so it is only ever called
     * from an explicit press — never from [refresh].
     */
    fun checkProviders() {
        if (_state.value.providersChecking) return
        viewModelScope.launch {
            _state.update { it.copy(providersChecking = true, providersError = null) }
            runCatching { scheduler.providers() }
                .onSuccess { health ->
                    _state.update { it.copy(providers = health, providersChecking = false) }
                }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not check the providers" }
                    _state.update {
                        it.copy(
                            providersChecking = false,
                            // The message, not a generic « failed »: the scheduler forwards the
                            // gateway's own sentence, and that sentence is the answer.
                            providersError = failure.message ?: "…",
                        )
                    }
                }
        }
    }

    /** Starts a scheduled mission now, without waiting for its cron. */
    fun runScheduled(name: String) {
        viewModelScope.launch {
            runCatching { scheduler.run(name) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not start $name" } }
            refresh()
        }
    }

    /** Suspends a mission, or puts it back — its history survives either way. */
    fun setScheduledEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { scheduler.setEnabled(name, enabled) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not toggle $name" } }
            refresh()
        }
    }

    /** Ce que le moteur a répondu à `GET /agent` — gardé pour résoudre le profil au lancement. */
    private var profils: List<EngineAgentProfile> = emptyList()

    /**
     * Fetches the model catalogue, once.
     *
     * Called when the New-mission sheet opens. A failure is **swallowed on purpose**: not being
     * able to list the models must not stop someone from launching a mission — it costs the
     * choice, not the feature, and the mission then runs on the profile's own model. Turning this
     * into the tab's red banner would report « the engine is unreachable » on a screen whose
     * mission list had just loaded fine.
     */
    fun loadModels() {
        if (_state.value.models.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { repository.models() }
                .onSuccess { choice ->
                    _state.update { it.copy(models = choice.models, preselectedModel = choice.preselected) }
                }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not list the engine's models" }
                }
        }
    }

    /**
     * Fetches the connector catalogue, once, when the New-mission sheet opens.
     *
     * Unlike the models, a failure here is **not** swallowed into an empty list. The sheet used to
     * offer four connectors from a table written by hand here — out of the platform's nineteen —
     * naming tools that do not exist for `fichiers`. Nothing failed and the mission launched with an
     * empty toolbox (30/08/2026). An empty picker would reproduce exactly that outcome from a
     * different cause, so the sheet says the catalogue is missing instead of pretending the
     * platform has nothing to offer.
     */
    fun loadConnectors() {
        if (_state.value.catalogue.connecteurs.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { repository.connectors() }
                .onSuccess { catalogue -> _state.update { it.copy(catalogue = catalogue) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not list the engine's connectors" }
                    _state.update { it.copy(connectorsFailed = true) }
                }
        }
    }

    fun launch(
        objective: String,
        connectors: List<String>,
        autonomous: Boolean,
        model: EngineModelRef? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.launch(
                    missionProfile(profils),
                    objective,
                    connectors,
                    autonomous = autonomous,
                    model = model,
                )
            }
                .onSuccess { refresh() }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not start the mission" }
                    _state.update { it.copy(loading = false, error = failure.engineFailureKind()) }
                }
        }
    }

    private companion object {
        /** A week: long enough to see a trend, short enough that today still stands out. */
        const val CONSUMPTION_DAYS = 7
    }

    fun abort(sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.abort(sessionId) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not stop $sessionId" } }
            refresh()
        }
    }
}

/** Null when it worked — the state holds « no problem » rather than a success value nobody reads. */
private fun EngineSignInResult.asProblem(): EngineSignInProblem? = when (this) {
    EngineSignInResult.Authorized -> null
    EngineSignInResult.NotConfigured -> EngineSignInProblem.NOT_CONFIGURED
    EngineSignInResult.NoCallbackHost -> EngineSignInProblem.NO_CALLBACK_HOST
    is EngineSignInResult.PortalUnreachable -> EngineSignInProblem.PORTAL_UNREACHABLE
    is EngineSignInResult.Refused -> EngineSignInProblem.REFUSED
    is EngineSignInResult.Interrupted -> EngineSignInProblem.INTERRUPTED
    is EngineSignInResult.MissingAuthorizationScope -> EngineSignInProblem.MISSING_SCOPE
}

/** Le profil du moteur sur lequel toute mission de l'application tourne. Déclaré côté serveur dans
 * `config/opencode/opencode.json` ; les deux noms ne doivent pas dériver. */
internal const val MISSION_PROFILE = "mission"

/**
 * Le profil à utiliser, sans demander à la personne.
 *
 * Décision du 25/08 : le choix de profil disparaît de la feuille « nouvelle mission ». Ce qu'une
 * mission FAIT est son objectif ; ce qu'elle PEUT faire est la liste de connecteurs cochés — les
 * règles de permission par session priment sur celles du profil, donc le choix ne décidait ni de
 * l'un ni de l'autre. Il offrait en revanche `compaction`, le résumeur interne d'OpenCode, comme
 * profil de mission.
 *
 * L'ordre des replis compte :
 *  1. le profil générique [MISSION_PROFILE], s'il est déployé ;
 *  2. sinon le premier profil DÉCLARÉ — ni natif, ni caché, ni sous-agent — pour qu'un moteur pas
 *     encore à jour continue de lancer des missions au lieu d'échouer sur un nom inconnu ;
 *  3. sinon [MISSION_PROFILE] quand même : le moteur refusera avec une erreur que l'écran sait
 *     déjà montrer, ce qui vaut mieux qu'un lancement silencieusement impossible.
 */
internal fun missionProfile(profiles: List<EngineAgentProfile>): String =
    profiles.firstOrNull { it.name == MISSION_PROFILE }?.name
        ?: profiles.firstOrNull { !it.native && !it.hidden && it.mode != "subagent" }?.name
        ?: MISSION_PROFILE
