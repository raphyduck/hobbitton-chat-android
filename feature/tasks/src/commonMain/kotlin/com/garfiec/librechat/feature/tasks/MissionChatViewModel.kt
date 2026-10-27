package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.diagnostics.threadReport
import com.garfiec.librechat.core.common.flow.batched
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AttentionSignals
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.ConversationModelStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.OpenConversation
import com.garfiec.librechat.core.data.engine.TITULAR_LEVEL
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.data.pricing.ModelPriceCache
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.model.scheduler.ModelTiers
import com.garfiec.librechat.core.model.scheduler.asModelRef
import com.garfiec.librechat.feature.tasks.delegate.CatalogueFetch
import com.garfiec.librechat.feature.tasks.delegate.ComposerStagingDelegate
import com.garfiec.librechat.feature.tasks.delegate.MissionCatalogueDelegate
import com.garfiec.librechat.feature.tasks.util.AudioNote
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.QuestionDraft
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.garfiec.librechat.feature.tasks.util.asPromptPart
import com.garfiec.librechat.feature.tasks.util.knowsPart
import com.garfiec.librechat.feature.tasks.util.outgoingMessageText
import com.garfiec.librechat.feature.tasks.util.reduce
import com.garfiec.librechat.feature.tasks.util.withPendingQuestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * One mission session, as a conversation you can talk to.
 *
 * Opening it does two things in order: fetch the transcript — the only place a mission's past lives —
 * and subscribe to the engine's feed for what happens next. Both arrive as the same event type, so
 * [chat] is one fold with no seam between them.
 */
data class MissionChatUiState(
    val chat: MissionChatState = MissionChatState(),
    val input: String = "",
    /** The transcript is still loading: the screen shows a spinner rather than a false empty state. */
    val loadingHistory: Boolean = true,
    /**
     * A re-seed asked for by the user, or by the screen coming back to the foreground.
     *
     * Separate from [loadingHistory] because the two draw differently: the first opening shows a
     * centred spinner over an empty screen, a re-seed shows the pull indicator over the transcript
     * that is already there.
     */
    val refreshing: Boolean = false,
    /** A send is in flight — the gap between the POST and the answer's first token. */
    val sending: Boolean = false,
    /** Photos staged for the next message, already downscaled by the picker's platform side. */
    val attachments: List<StagedAttachment> = emptyList(),
    /** Audio files already transcribed, leaving with the next message as quoted blocks in the thread. */
    val audioNotes: List<AudioNote> = emptyList(),
    /**
     * Some audio is at the scheduler's transcription right now — a dictation about to land in
     * [input], or a deposited file about to join [audioNotes]. One flag for both: one at a time.
     */
    val transcribing: Boolean = false,
    /**
     * Why the last transcription failed, or null — the one error here that is not the engine's.
     * Carries the server's own words when it gave some, shown under the translated reason.
     */
    val transcriptionError: TranscriptionOutcome.Failed? = null,
    /** The mic heard by the phone itself (Settings, on by default) rather than recorded for the scheduler. */
    val localDictation: Boolean = true,
    /** Why the transcript would not load, or null. */
    val historyError: EngineFailureKind? = null,
    /**
     * The transcript on screen is the conversation's tail only (26/10/2026, Raphaël: switching
     * conversations took too long): the last [HISTORY_TAIL] messages were read, and « Messages
     * précédents » at the top reads the whole transcript on demand.
     */
    val historyTruncated: Boolean = false,
    /** The whole transcript is being read, after a tap on « Messages précédents ». */
    val loadingEarlier: Boolean = false,
    /** Why the last send did not reach the engine, or null. The text is put back when this is set. */
    val sendError: EngineFailureKind? = null,
    /** The connectors this deployment offers, fetched from the scheduler — never a local copy. */
    val connectors: List<ConnectorOption> = emptyList(),
    /**
     * Which of them this session currently carries, read off the engine — **null until read**.
     *
     * The distinction is the whole point. This started as an empty set that only the user's own
     * ticks ever filled, so every session opened claiming « No connector », including the ones the
     * scheduler had launched with nine. Null says « not known yet » and the chip says so too;
     * an empty set now means the engine really did answer « none ».
     */
    val enabledConnectors: Set<String>? = null,
    /**
     * The trust level the scheduler recorded for this session (D-083), null while unknown. A
     * session not at the titulaire's level (a scheduled run) is lifted to it before the person's
     * first message in it: it is now them speaking, and what the annuaire serves follows.
     */
    val sessionLevel: String? = null,
    /** The models a message may be sent on. */
    val models: List<EngineSelectableModel> = emptyList(),
    /**
     * What each of them costs, in dollars per million tokens — the same table the chat's picker
     * shows, so one model reads the same price on both screens.
     */
    val prices: ModelPrices = ModelPrices.NONE,
    /**
     * The model the user picked for the next message, or null to leave the session on its own.
     *
     * Deliberately NOT seeded from the deployment's catalogue default. That seeding is what made the
     * chip lie until 30/08/2026: it named the first declared provider's default on every session,
     * including the many that had never run on it. Null here means « the model on the chip », which
     * the next message names explicitly — see [nextModel].
     */
    val model: EngineSelectableModel? = null,
    /**
     * The model this device last sent this conversation with (26/10/2026), ahead of the one the
     * last turn ran on: a reminder or a relance the scheduler played into the conversation ran on
     * its own model, and the person's next message used to follow it.
     */
    val rememberedModel: EngineModelRef? = null,
    /**
     * The connector catalogue would not load. The chip says so instead of offering an empty list —
     * an empty connector sheet reads as « this mission can have nothing », which is a different
     * claim, and the very outcome this screen exists to have fixed.
     */
    val connectorsError: EngineFailureKind? = null,
    /** The model list would not load. Independent of [connectorsError]: different host. */
    val modelsError: EngineFailureKind? = null,
    /**
     * The chat's own font-size setting, applied here too. One knob for both conversations —
     * a reader on LARGE was getting normal-size text in this tab only.
     */
    val fontScale: Float = 1f,
    /**
     * The first name the portal knows the person by, for the greeting of a new conversation
     * (10/10/2026). Null until read, and null for good when the scheduler has no name to give: the
     * greeting then stands without one.
     */
    val greetingName: String? = null,
    /**
     * The conversations' provider default, shown on the chip of a conversation that has not run
     * yet, so a new one names the model it is about to use.
     */
    val defaultModel: EngineSelectableModel? = null,
    /** The engine's own default: what « Auto » runs on when the scheduler names no model. */
    val engineDefault: EngineSelectableModel? = null,
    /**
     * The tiers of model the scheduler offers a conversation (25/10/2026), lowest first, for
     * « redo stronger »; empty without a scheduler, and the button stays away.
     */
    val tiers: ModelTiers = ModelTiers.NONE,
    /**
     * Set once a **new** chat exists on the engine: the screen hands it to the navigation, which
     * replaces the blank conversation with the real one. Null for an existing session.
     */
    val started: StartedChat? = null,
    /** A conversation its first message has yet to create. */
    val isNew: Boolean = false,
    /**
     * The person's answer to the first pending question, as they fill the form, kept here rather
     * than in the composable so a rotation does not wipe it. Null, or naming another request,
     * means a blank form ([questionDraftFor]).
     */
    val questionDraft: QuestionDraft? = null,
    /** An answer or a dismissal is on its way to the engine. */
    val answeringQuestion: Boolean = false,
    /** Why the last answer did not reach the engine, or null. The form stays, filled as it was. */
    val questionError: EngineFailureKind? = null,
) {
    /** The question the form shows: the oldest one the agent is waiting on. */
    val pendingQuestion get() = chat.questions.firstOrNull()

    /** The draft for [pendingQuestion], blank when none was started for it. */
    fun questionDraftFor(request: EngineQuestionRequest): QuestionDraft =
        questionDraft?.takeIf { it.requestId == request.id } ?: QuestionDraft.blank(request)

    /**
     * What the model chip says, and what the picker shows as current.
     *
     * The user's pick for the next message wins; failing that, the model this device last sent the
     * conversation with ([rememberedModel]); failing that, the model the session's last turn
     * actually ran on — read off the engine's own message envelopes. A model the catalogue does not list still gets named, by its raw id: an
     * unfamiliar model is worth showing, and « Session model » in its place says less than nothing.
     */
    val effectiveModel: EngineSelectableModel?
        get() = model ?: (rememberedModel ?: chat.model)?.let { ref ->
            models.firstOrNull { it.ref == ref }
                ?: EngineSelectableModel(
                    providerId = ref.providerId,
                    modelId = ref.modelId,
                    label = ref.modelId,
                )
        } ?: defaultModel

    /**
     * The model one step up the scheduler's ladder from the one the conversation runs on, for
     * « redo stronger » (25/10/2026); null at the top, off the ladder, or while « Auto » is still
     * unresolved. Named from the catalogue when it lists it, by its raw id otherwise.
     */
    val strongerModel: EngineSelectableModel?
        get() {
            val current = effectiveModel?.takeUnless { it.isAuto } ?: return null
            val (_, qualified) = tiers.strongerThan(current.modelId) ?: return null
            val ref = qualified.asModelRef() ?: return null
            return models.firstOrNull { it.modelId == ref.modelId }
                ?: EngineSelectableModel(providerId = ref.providerId, modelId = ref.modelId, label = ref.modelId)
        }

    /** The last thing the person wrote, what « redo stronger » asks again; null when nothing was. */
    val lastUserText: String?
        get() = chat.turns.filterIsInstance<ChatTurn.User>().lastOrNull()
            ?.parts?.filterIsInstance<ChatPart.Text>()?.joinToString("\n") { it.text }
            ?.takeIf { it.isNotBlank() }

    /** « Redo stronger » is offered: an answer is in, nothing runs, and a stronger tier exists. */
    val canRedoStronger: Boolean
        get() = !chat.streaming && !sending && chat.turns.lastOrNull() is ChatTurn.Assistant &&
            lastUserText != null && strongerModel != null
}

/** A chat the composer just created on the engine, as the navigation needs it. */
data class StartedChat(val sessionId: String, val title: String)

/**
 * What the navigation hands the conversation's view model: which session (none for a
 * conversation not started yet). A value rather than a bare nullable string, so the Koin lookup is
 * by this type and cannot confuse a missing session id with something else.
 */
data class MissionChatArgs(val sessionId: String?)

class MissionChatViewModel(
    /**
     * The session on screen, or null for a conversation that does not exist yet (D-077): its first send
     * creates it ([EngineMissionRepository.startChat]) and the navigation then opens the real one.
     */
    private val sessionId: String?,
    private val repository: EngineMissionRepository,
    private val modelPrices: ModelPriceCache,
    private val settings: SettingsDataStore,
    /** Speech to text for the dictation and the audio files: the scheduler's, since D-077. */
    private val transcriber: AudioTranscriber,
    /** Sound and notification when a reply this screen was waiting on finishes out of sight. */
    private val attention: AttentionSignals,
    private val ioDispatcher: CoroutineDispatcher,
    /** Who the portal says is signed in, for the greeting of a new chat; null where unavailable. */
    private val scheduler: SchedulerRepository? = null,
    /** The model this device last sent each conversation with (26/10/2026); null in tests. */
    private val conversationModels: ConversationModelStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MissionChatUiState(
            // Nothing to load for a chat that does not exist yet.
            loadingHistory = sessionId != null,
            isNew = sessionId == null,
        ),
    )
    val uiState: StateFlow<MissionChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    /** The transcript's read in progress: a reconciliation leaves it alone, a refresh replaces it. */
    private var historyJob: Job? = null

    /**
     * A turn this screen saw start (a message sent from it, or a reply streaming in live) whose
     * end has not come yet. Its `Idle` is the « reply ready » signal; an `Idle` for a turn nobody
     * here watched start (a re-read, the feed's own reconnect) rings nothing.
     */
    private var awaitingReply = false

    /** Sends still waiting for their turn's end: more than one when a message is added mid-turn. */
    private var sendsInFlight = 0

    // Declared before `init`, which reads the catalogue: initializers run in textual order.
    private val catalogueLoader = MissionCatalogueDelegate(
        fetchModels = { repository.models() },
        fetchPrices = { modelPrices.prices() },
        fetchConnectors = { repository.connectors() },
        context = ioDispatcher,
    )

    private val staging = ComposerStagingDelegate(
        state = _uiState,
        scope = viewModelScope,
        transcribe = { bytes, mime, filename -> transcriber.transcribe(bytes, mime, filename) },
        context = ioDispatcher,
    )

    /**
     * How much of the transcript the screen reads (26/10/2026): the last [HISTORY_TAIL] messages
     * at opening, the whole of it once « Messages précédents » was tapped, refreshes included.
     * Declared before `init`, which reads it: a property below the block is null when it runs.
     */
    private var historyLimit: Int? = HISTORY_TAIL

    /**
     * Where the opening stands, for the guard and the journal; [openingSteps] moves at each step.
     * Declared before `init` too: the opening's first step runs from it.
     */
    private var openingStage = "starting"
    private var openingSteps = 0

    init {
        if (sessionId != null) {
            // Written before anything is asked of another thread (27/10/2026): an opening that
            // stalls says so in the journal, where the 27/10 one left no line at all.
            Logger.i(LOG_TAG) { "History of $sessionId: opening" }
            loadHistory()
            watchOpening(sessionId)
            openStream()
            viewModelScope.launch {
                val remembered = runCatching { withContext(ioDispatcher) { conversationModels?.modelOf(sessionId) } }.getOrNull()
                if (remembered != null) _uiState.update { it.copy(rememberedModel = remembered) }
            }
        } else {
            // A new conversation has no transcript to wait for. An existing one loads its catalogue
            // once its transcript has landed (26/10/2026): the models, the connectors, the session's
            // level and the prices are five more requests, which used to leave with the transcript
            // and share its bandwidth on a weak link; the screen is the transcript.
            loadCatalogue()
        }
        // A new conversation greets by name: one small read, and a greeting without a name if it fails.
        if (sessionId == null) {
            viewModelScope.launch(ioDispatcher) {
                val name = scheduler?.identity()?.firstName ?: return@launch
                _uiState.update { it.copy(greetingName = name) }
            }
        }
        viewModelScope.launch {
            settings.chatFontSize.collect { size ->
                _uiState.update { it.copy(fontScale = size.multiplier) }
            }
        }
        viewModelScope.launch {
            settings.localDictation.collect { local ->
                _uiState.update { it.copy(localDictation = local) }
            }
        }
    }

    /**
     * What the composer needs to offer: the connectors this deployment has, and the models a
     * message can be sent on.
     *
     * **Two fetches, two failures, on purpose.** They come from two different hosts — the connector
     * catalogue from the scheduler, the model list from the engine — so one being unreachable must
     * not take the other's picker down with it. Folding them into a single `try` did exactly that
     * on 30/08/2026: the scheduler had not yet been redeployed with its `connecteurs` tool, and the
     * model chip vanished along with the connector chip, on an engine that was answering fine.
     *
     * Either failure costs its own picker and nothing else: the transcript and the send box work
     * without both.
     */
    private fun loadCatalogue() {
        if (catalogueRequested) return
        catalogueRequested = true
        loadConnectors()
        loadModels()
    }

    /** The catalogue is read once per screen: the transcript's retries must not read it again. */
    private var catalogueRequested = false

    /**
     * The catalogue, and what this session already holds out of it.
     *
     * Both, or the chip lies: the list alone says what *could* be granted, and the screen used to
     * fill in « what is granted » from nothing but the user's own ticks. The two land in a single
     * update so the chip never renders a catalogue against an unknown grant.
     *
     * The grant read is allowed to fail on its own — it is a second route on the same host — and
     * then the ticks stay unknown rather than becoming a false « none ».
     */
    private fun loadConnectors() {
        viewModelScope.launch {
            when (val fetched = catalogueLoader.connectors()) {
                is CatalogueFetch.Failed -> _uiState.update { it.copy(connectorsError = fetched.kind) }
                is CatalogueFetch.Loaded -> {
                    // A conversation not started yet holds nothing: it starts from what the scheduler
                    // ticks by default (since D-082 everything but the shell), and the person
                    // adjusts before sending.
                    val scopeRead = if (sessionId == null) {
                        null
                    } else {
                        runCatching {
                            withContext(ioDispatcher) { repository.sessionScope(sessionId) }
                        }
                    }
                    val scope = scopeRead?.getOrNull()
                    // The session's grant could not be read: say so on the chip, with its retry,
                    // rather than draw no chip at all (26/10/2026: the composer showed the model
                    // alone and nothing to tick connectors with).
                    scopeRead?.exceptionOrNull()?.let { failure ->
                        _uiState.update { it.copy(connectorsError = failure.engineFailureKind()) }
                        return@launch
                    }
                    val granted = if (sessionId == null) {
                        fetched.value.offered().filter { it.tickedByDefault }.map { it.name }.toSet()
                    } else {
                        scope?.connectors
                    }
                    _uiState.update {
                        it.copy(
                            // Someone is watching this conversation, so nothing is barred as it would
                            // be for an unattended mission (brief §4.2).
                            connectors = fetched.value.offered(),
                            // A tick the user made while this was in flight outranks what the engine
                            // said a moment ago: it has already been sent, and overwriting it here
                            // would undo a checkbox under their finger.
                            enabledConnectors = it.enabledConnectors ?: granted,
                            // A lift done meanwhile (a send) outranks the level read before it.
                            sessionLevel = it.sessionLevel ?: scope?.level,
                            connectorsError = null,
                        )
                    }
                }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            when (val fetched = catalogueLoader.models()) {
                is CatalogueFetch.Failed -> _uiState.update { it.copy(modelsError = fetched.kind) }
                is CatalogueFetch.Loaded -> {
                    _uiState.update {
                        it.copy(
                            models = fetched.value.models,
                            modelsError = null,
                            defaultModel = fetched.value.preselected,
                            engineDefault = fetched.value.engineDefault,
                        )
                    }
                    // After the models and never instead of them: the price table comes from another
                    // service, and its absence must cost the prices, not the picker.
                    val prices = catalogueLoader.prices()
                    _uiState.update { it.copy(prices = prices) }
                    // The ladder of tiers (25/10/2026), for « redo stronger »: the scheduler's, and
                    // its absence costs the button, nothing else.
                    val tiers = withContext(ioDispatcher) { runCatching { scheduler?.modelTiers() }.getOrNull() }
                    _uiState.update { it.copy(tiers = tiers ?: ModelTiers.NONE) }
                }
            }
        }
    }

    /** Retries only what failed — a working picker is not re-fetched to heal a broken one. */
    fun retryCatalogue() {
        if (_uiState.value.connectorsError != null) {
            _uiState.update { it.copy(connectorsError = null) }
            loadConnectors()
        }
        if (_uiState.value.modelsError != null) {
            _uiState.update { it.copy(modelsError = null) }
            loadModels()
        }
    }

    /**
     * Grants or revokes a connector on the live session.
     *
     * The screen moves first and the engine follows: a tick that waited for a round trip reads as a
     * broken checkbox. If the call fails the tick is rolled back, because leaving it on would
     * promise a capability the session does not have — and the next message would then fail for a
     * reason nothing on screen explains.
     */
    fun toggleConnector(name: String) {
        val before = _uiState.value.enabledConnectors.orEmpty()
        val after = if (name in before) before - name else before + name
        _uiState.update { it.copy(enabledConnectors = after) }
        // A conversation not started yet keeps its ticks here: its first message carries them.
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.setConnectors(sessionId, after.toList()) }
                _uiState.update { it.copy(sessionLevel = TITULAR_LEVEL) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(enabledConnectors = before, sendError = e.engineFailureKind()) }
            }
        }
    }

    /** Which model the *next* message runs on. The engine takes it per message, not per session. */
    fun selectModel(model: EngineSelectableModel?) {
        _uiState.update { it.copy(model = model) }
    }

    /**
     * The transcript: first the tail the device kept of it, shown at once (26/10/2026, Raphaël:
     * « quand tu reviens dans une conversation qu'on a déjà ouverte, il ne redemande pas
     * nécessairement le reste »), then the engine's, unless the session has not moved since; a
     * [refresh] reads the engine whatever the device kept.
     *
     * No watchdog on the opening (26/10/2026 evening): the one of v2026.10.48 watched this
     * ViewModel's progress while the placeholders Raphaël filmed came from a screen that had
     * loaded everything and was not collecting it (see [MissionChatScreen]). The reads' own
     * limits are all the opening needs.
     */
    private fun loadHistory(trustCache: Boolean = true, seedFromCache: Boolean = trustCache) {
        val sessionId = sessionId ?: return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                val limit = historyLimit
                var cacheLate = false
                val kept = if (trustCache && limit != null) {
                    advanceOpening("reading what the device kept")
                    // Bounded (27/10/2026): a file of a few hundred kilobytes, read in a few
                    // milliseconds when the threads answer. When they do not, the engine is asked
                    // without it rather than the screen waiting on a copy.
                    val read = withTimeoutOrNull(CACHE_READ_MS) {
                        KeptRead(withContext(ioDispatcher) { repository.cachedHistory(sessionId) })
                    }
                    cacheLate = read == null
                    read?.history
                } else {
                    null
                }
                if (cacheLate) {
                    Logger.w(LOG_TAG) {
                        "History of $sessionId: what the device kept did not answer in $CACHE_READ_MS ms, " +
                            "reading the engine; ${threadReport()}"
                    }
                } else if (trustCache) {
                    Logger.i(LOG_TAG) {
                        val what = kept?.let { "${it.messageCount} messages kept on the device" } ?: "nothing kept on the device"
                        "History of $sessionId: $what"
                    }
                }
                if (kept != null && seedFromCache) {
                    _uiState.update { current ->
                        val seeded = kept.events.fold(current.chat) { state, event -> state.reduce(event) }
                        val truncated = kept.limit?.let { kept.messageCount >= it } ?: false
                        current.copy(chat = seeded, loadingHistory = false, historyTruncated = truncated)
                    }
                }
                val read = fetchHistory(sessionId, limit, unlessUpdatedAt = kept?.updated)
                advanceOpening("folding the transcript")
                if (kept != null && read !is HistoryRead.Fresh) {
                    // The engine said the conversation has not moved, or could not be reached: what
                    // the device kept stands, and the catalogue and the questions follow as usual.
                    _uiState.update { it.copy(loadingHistory = false, refreshing = false) }
                    loadCatalogue()
                    loadPendingQuestions(sessionId)
                    return@launch
                }
                val events = (read as? HistoryRead.Fresh)?.events
                if (events == null) {
                    Logger.w(LOG_TAG) { "History of $sessionId: no answer after $HISTORY_ATTEMPTS attempts" }
                    _uiState.update {
                        it.copy(loadingHistory = false, refreshing = false, historyError = EngineFailureKind.UNREACHABLE)
                    }
                    loadCatalogue()
                    return@launch
                }
                _uiState.update { current ->
                    // Fold the past *under* whatever the live feed already delivered, so nothing that
                    // arrived while we were fetching is lost.
                    val seeded = events.fold(current.chat) { state, event -> state.reduce(event) }
                    // As many messages as asked for: there may be more before them.
                    val truncated = limit != null && events.count { it is EngineStreamEvent.MessageStarted } >= limit
                    current.copy(chat = seeded, loadingHistory = false, refreshing = false, historyTruncated = truncated)
                }
                loadCatalogue()
                loadPendingQuestions(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(LOG_TAG, e) { "History of $sessionId failed" }
                _uiState.update {
                    it.copy(
                        loadingHistory = false,
                        refreshing = false,
                        historyError = e.engineFailureKind(),
                    )
                }
                loadCatalogue()
            }
        }
    }

    /**
     * The transcript, in up to [HISTORY_ATTEMPTS] tries of [HISTORY_ATTEMPT_MS] each. A try that
     * hangs says the connection it went on is dead (26/10/2026: after a sleep or a change of
     * network, the HTTP/2 connection everything multiplexes on stays in OkHttp's pool, and every
     * try queued on it; the screen stayed on its placeholders for 30 s and worked on a later tap):
     * every connection is dropped before the next try, which opens a live one.
     */
    private suspend fun fetchHistory(sessionId: String, limit: Int?, unlessUpdatedAt: Long? = null): HistoryRead? {
        repeat(HISTORY_ATTEMPTS) { attempt ->
            advanceOpening("reading the engine, attempt ${attempt + 1}")
            val started = Clock.System.now().toEpochMilliseconds()
            val read = try {
                // Every try gets the same limit (26/10/2026 evening): a first try cut at 3 s took
                // the 2 to 5 s reads of a slow link for hung connections, and dropping every
                // connection then cancelled the catalogue and the drawer's reads with them. The
                // tail the device kept shows the conversation meanwhile.
                withTimeoutOrNull(HISTORY_ATTEMPT_MS) {
                    withContext(ioDispatcher) {
                        if (unlessUpdatedAt != null && limit != null) {
                            repository.historyUnlessUpdatedAt(sessionId, limit, unlessUpdatedAt)
                                ?.let { HistoryRead.Fresh(it) } ?: HistoryRead.Unchanged
                        } else {
                            HistoryRead.Fresh(repository.history(sessionId, limit))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A read cut by the network (the connections dropped on a change of network, a
                // host the phone could not resolve for a second) is asked again, not shown as an
                // error (26/10/2026); anything else (a refusal, a missing session) is.
                val last = attempt == HISTORY_ATTEMPTS - 1
                if (last || e.engineFailureKind() != EngineFailureKind.UNREACHABLE) throw e
                Logger.w(LOG_TAG, e) { "History of $sessionId: attempt ${attempt + 1} cut, asking again" }
                delay(HISTORY_RETRY_PAUSE_MS)
                return@repeat
            }
            if (read != null) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - started
                Logger.i(LOG_TAG) {
                    val what = (read as? HistoryRead.Fresh)
                        ?.let { fresh -> "${fresh.events.count { it is EngineStreamEvent.MessageStarted }} messages" }
                        ?: "unchanged"
                    "History of $sessionId: $what in $elapsed ms (attempt ${attempt + 1})"
                }
                return read
            }
            Logger.w(LOG_TAG) { "History of $sessionId: attempt ${attempt + 1} hung, dropping every connection" }
            advanceOpening("dropping every connection after attempt ${attempt + 1}")
            dropConnections()
        }
        return null
    }

    /** What a transcript read came back with; null from [fetchHistory] when every attempt hung. */
    private sealed interface HistoryRead {
        data class Fresh(val events: List<EngineStreamEvent>) : HistoryRead

        /** The device's tail is current: nothing to fold. */
        data object Unchanged : HistoryRead
    }

    /** What the device's copy answered, so that « nothing kept » and « no answer » stay apart. */
    private class KeptRead(val history: EngineMissionRepository.CachedHistory?)

    /**
     * Every connection dropped, within [DROP_CONNECTIONS_MS] (27/10/2026): closing a dead link is
     * itself I/O, and an opening must not wait on it. What is left closes in the background.
     */
    private suspend fun dropConnections() {
        try {
            withTimeoutOrNull(DROP_CONNECTIONS_MS) { repository.dropConnections() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(LOG_TAG, e) { "Dropping the connections failed" }
        }
    }

    private fun advanceOpening(stage: String) {
        openingStage = stage
        openingSteps++
    }

    /**
     * The opening's guard (27/10/2026). Every step of the opening is bounded (the device's copy
     * [CACHE_READ_MS], each read of the engine [HISTORY_ATTEMPT_MS]), so a step that has not moved
     * for [OPENING_GUARD_MS] is waiting on something no bound covers: the journal gets the step and
     * the process's threads, every connection is dropped and the engine is read again, as the person
     * did by leaving and coming back. At most [OPENING_RESCUES] times; a screen past its
     * placeholders, or an opening that ended in an error, is left alone.
     *
     * Not v2026.10.48's guard, removed the next evening: that one watched this ViewModel while the
     * screen of the day was not collecting it. The screen collects whatever its entry's lifecycle
     * says since v2026.10.49, so what it shows is this state.
     */
    private fun watchOpening(sessionId: String) {
        viewModelScope.launch {
            var seen = openingSteps
            var rescues = 0
            while (rescues < OPENING_RESCUES) {
                delay(OPENING_GUARD_MS)
                val state = _uiState.value
                if (!state.loadingHistory || state.chat.turns.isNotEmpty() || state.historyError != null) return@launch
                if (openingSteps != seen) {
                    seen = openingSteps
                    continue
                }
                rescues++
                Logger.w(LOG_TAG) {
                    "History of $sessionId: still on the placeholders, « $openingStage » for ${OPENING_GUARD_MS / 1000} s; " +
                        "dropping every connection and reading again; ${threadReport()}"
                }
                dropConnections()
                loadHistory(trustCache = false)
                seen = openingSteps
            }
        }
    }

    /**
     * The questions the agent is already waiting on. The feed announces a question once: a screen
     * opened after it (a new chat whose first turn asked straight away, a task opened from the
     * drawer, a feed that dropped) would show a session « running » for ever, its form nowhere.
     * A failure costs the catch-up only; the feed still brings the next question.
     */
    private suspend fun loadPendingQuestions(sessionId: String) {
        val pending = runCatching { withContext(ioDispatcher) { repository.pendingQuestions(sessionId) } }
            .getOrNull() ?: return
        if (pending.isNotEmpty()) _uiState.update { it.copy(chat = it.chat.withPendingQuestions(pending)) }
    }

    /**
     * Back in front after a pause (26/10/2026 evening): the session is asked whether it moved or
     * works since the tail the device kept, and the transcript read only then, folded under what
     * is on screen; the screen itself is left as it is meanwhile. Before, every return read the
     * whole tail again and showed the refresh indicator.
     */
    fun resume() = loadHistory(trustCache = true, seedFromCache = false)

    /**
     * Re-read the transcript on an open screen.
     *
     * WHY THIS HAS TO EXIST. The screen fills from two places that only meet once: [loadHistory] at
     * open, and the live feed. The engine's classic feed has **no resume cursor** — a reconnect
     * resumes at « now » (see core/network's CLAUDE.md). So everything a mission emits while the
     * feed is down is never re-delivered, and the open screen stays amputated: the only way back
     * was to leave for the list and return, which destroys the ViewModel and re-runs [loadHistory].
     * Signalé par Raphaël le 21/09/2026, dans ces termes exactement.
     *
     * The stuck spinner has the same root. [MissionChatState.streaming] is raised by a delta and
     * lowered **only** by `Idle`; a feed that drops between the two leaves a turn marked « running »
     * for good. Re-seeding therefore lowers it: the transcript is the arbiter of what is finished,
     * and a turn that really is live raises it again on its next delta.
     *
     * Safe to replay: the reducer is idempotent — a message or part seen twice is updated in place —
     * and `engineHistoryEvents` replays full `PartUpdated` parts, never deltas, so nothing is
     * appended twice.
     */
    fun refresh() {
        if (sessionId == null) return
        _uiState.update { it.copy(refreshing = true, chat = it.chat.copy(streaming = false)) }
        loadHistory(trustCache = false)
    }

    /**
     * Subscribe to the engine's feed. The flow never throws — a dead feed simply ends — so a failure
     * there leaves the transcript on screen instead of tearing the collector down.
     */
    private fun openStream() {
        val sessionId = sessionId ?: return
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            // A delta per character and a redraw per delta (26/10/2026 evening): the deltas of a
            // frame or so go into the state together, every other event at once, in order.
            repository.events(sessionId)
                .flowOn(ioDispatcher)
                .batched(DELTA_BATCH_MS) { it is EngineStreamEvent.PartDelta }
                .collect { events ->
                    if (events.any { it.isOrphanDelta() }) reconcileHistory()
                    _uiState.update { state -> state.copy(chat = events.fold(state.chat) { chat, event -> chat.reduce(event) }) }
                    events.forEach { watchForReply(sessionId, it) }
                }
        }
    }

    /** A text delta on a part this screen was never told of: the feed was joined after the part began. */
    private fun EngineStreamEvent.isOrphanDelta(): Boolean =
        this is EngineStreamEvent.PartDelta && field == "text" && !_uiState.value.chat.knowsPart(messageId, partId)

    /**
     * The transcript, asked again because the feed named a part the screen does not know
     * (26/10/2026): the screen joined the feed after the part was announced (a conversation opened
     * while its session worked, a feed that reconnected). The reducer draws nothing for such a
     * part, since it cannot tell an answer from a model's reasoning: the read names it, and the
     * deltas that follow land in the right place. Once every [RECONCILE_PAUSE_MS] at most, since the
     * deltas keep coming while the read is on its way.
     */
    private fun reconcileHistory() {
        // A read already on its way will name the part: starting over would cancel it, and while
        // the session writes, the deltas come faster than a slow read lands (26/10/2026 evening:
        // each reconciliation restarted the opening's read, none of them ever finished).
        if (historyJob?.isActive == true) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastReconcileMillis < RECONCILE_PAUSE_MS) return
        lastReconcileMillis = now
        loadHistory(trustCache = false)
    }

    private var lastReconcileMillis = 0L

    /**
     * « Reply ready », for a turn this screen saw start and that ended while the person was not
     * looking at it. Whether they were is [AttentionSignals]'s call, as is the Settings switch.
     */
    private fun watchForReply(sessionId: String, event: EngineStreamEvent) {
        when (event) {
            is EngineStreamEvent.PartDelta -> awaitingReply = true
            EngineStreamEvent.Idle -> if (awaitingReply) {
                awaitingReply = false
                viewModelScope.launch {
                    attention.replyReady(sessionId) {
                        OpenConversation(
                            sessionId = sessionId,
                            title = withContext(ioDispatcher) { repository.sessionTitle(sessionId) },
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    /** The screen is in front of the person: a question here chimes rather than notifies. */
    fun onVisible() {
        sessionId?.let(attention::enter)
    }

    fun onHidden() {
        sessionId?.let(attention::leave)
    }

    override fun onCleared() {
        onHidden()
        super.onCleared()
    }

    /** Ticks or unticks an option of the pending question's [index]-th question. */
    fun pickQuestionOption(index: Int, label: String) {
        editQuestionDraft { request, draft ->
            draft.pick(index, label, multiple = request.questions.getOrNull(index)?.multiple == true)
        }
    }

    /** The free answer of the pending question's [index]-th question. */
    fun typeQuestionAnswer(index: Int, text: String) {
        editQuestionDraft { request, draft ->
            draft.type(index, text, multiple = request.questions.getOrNull(index)?.multiple == true)
        }
    }

    private inline fun editQuestionDraft(
        edit: (EngineQuestionRequest, QuestionDraft) -> QuestionDraft,
    ) {
        _uiState.update { state ->
            val request = state.pendingQuestion ?: return@update state
            state.copy(questionDraft = edit(request, state.questionDraftFor(request)), questionError = null)
        }
    }

    /**
     * Sends the form's answers. The agent's turn resumes at once and streams on the feed. The form
     * goes as soon as the engine has the answer; the feed's `question.replied` would close it too,
     * a moment later.
     *
     * A question no longer waiting (answered from another device, or its turn stopped) closes the
     * same way: the repository reads the engine's 404 as « nothing left to answer ».
     */
    fun answerQuestion() {
        val state = _uiState.value
        val request = state.pendingQuestion ?: return
        val draft = state.questionDraftFor(request)
        if (state.answeringQuestion || !draft.isComplete()) return
        settleQuestion(request.id) {
            // An answer is the person speaking too (27/10/2026, the kit's review): a conversation
            // the scheduler opened stands at the mission's level until the titulaire says a word
            // (D-083), and the decision it asks for may need what only that level leaves out.
            sessionId?.let { liftToTitularIfNeeded(it, state) }
            repository.answerQuestion(request.id, draft.answers())
        }
    }

    /** Dismisses the pending question: the agent goes on without the answer. */
    fun dismissQuestion() {
        val state = _uiState.value
        val request = state.pendingQuestion ?: return
        if (state.answeringQuestion) return
        settleQuestion(request.id) { repository.dismissQuestion(request.id) }
    }

    private fun settleQuestion(requestId: String, call: suspend () -> Unit) {
        _uiState.update { it.copy(answeringQuestion = true, questionError = null) }
        // The turn resumes now: its end is a reply this screen is waiting on.
        awaitingReply = true
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { call() }
                closeQuestion(requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(questionError = e.engineFailureKind()) }
            } finally {
                _uiState.update { it.copy(answeringQuestion = false) }
            }
        }
    }

    /**
     * The person is now the one speaking to this session: a scheduled run, or a conversation the
     * scheduler opened, or any session not recorded at the titulaire's level, is lifted there
     * first, once, so the annuaire serves what the titulaire reaches (D-083; 24/10/2026, « Go ssh »
     * refused in a run that still stood at the mission's level). Its rules are rewritten only from
     * connectors the screen actually read: an unknown list (the scope read failed) lifts the level
     * alone, and the session keeps the tools it has (26/10/2026: an empty list had stripped every
     * one of them). Called by a send and by an answer to a question.
     */
    private suspend fun liftToTitularIfNeeded(sessionId: String, state: MissionChatUiState) {
        if (state.sessionLevel == TITULAR_LEVEL) return
        val known = state.enabledConnectors
        withContext(ioDispatcher) {
            if (known != null) repository.setConnectors(sessionId, known.toList()) else repository.liftToTitular(sessionId)
        }
        _uiState.update { it.copy(sessionLevel = TITULAR_LEVEL) }
    }

    private fun closeQuestion(requestId: String) {
        _uiState.update {
            it.copy(
                chat = it.chat.reduce(EngineStreamEvent.QuestionClosed(requestId)),
                questionDraft = it.questionDraft?.takeIf { draft -> draft.requestId != requestId },
            )
        }
        attention.questionClosed(requestId)
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    // The composer's staging — dictation, audio files, photos — lives in ComposerStagingDelegate.

    /** A dictation, transcribed into the composer — never sent on its own. */
    fun transcribeAudio(bytes: ByteArray, mime: String, filename: String) =
        staging.transcribeAudio(bytes, mime, filename)

    /** Words the phone itself heard, into the composer like a transcribed dictation. */
    fun dictated(text: String) = staging.dictated(text)

    /** The phone's own recognition gave up: said like a transcription's failure. */
    fun dictationFailed(failure: TranscriptionOutcome.Failed) = staging.dictationFailed(failure)

    /** A deposited audio file, transcribed into a quoted note for the thread. */
    fun attachAudio(bytes: ByteArray, mime: String, filename: String) = staging.attachAudio(bytes, mime, filename)

    fun removeAudioNote(id: String) = staging.removeAudioNote(id)

    fun dismissTranscriptionError() = staging.dismissTranscriptionError()

    fun addAttachments(staged: List<StagedAttachment>) = staging.addAttachments(staged)

    fun removeAttachment(id: String) = staging.removeAttachment(id)

    /**
     * Send, and reconcile.
     *
     * The call waits for the finished turn, but the turn also arrives on the feed meanwhile — so
     * the failure arm has to tell two different things apart. A send that never reached the engine
     * leaves the conversation exactly as it was, and the words belong back in the box. A send that
     * reached it and then lost the socket has *already* moved the conversation, and reporting « the
     * engine did not answer » over an answer visibly streaming in is the screen contradicting
     * itself — which is what it did on 30/08/2026, when the client's 30 s cap expired mid-turn.
     *
     * So the fold of the transcript is the arbiter: unchanged means nothing happened, changed means
     * the engine took it and only the reconciliation was lost.
     */
    fun send() {
        val staged = _uiState.value
        // The typed words plus each audio note as a quoted block — what the thread will show.
        val text = outgoingMessageText(staged.input, staged.audioNotes)
        val attachments = staged.attachments
        // A send while a turn runs (`sending`, or the reply streaming) is not refused (23/10/2026,
        // Raphaël): the engine records the message at once and its running turn reads it after the
        // step under way, without interrupting it; the call returns when that turn ends.
        if ((text.isEmpty() && attachments.isEmpty()) || staged.started != null) return
        val sessionId = sessionId ?: return start(text, staged)
        val input = staged.input
        val notes = staged.audioNotes
        val before = staged.chat
        // Mid-turn, the transcript moves anyway: whether this send reached the engine is told by
        // its own words appearing as a user turn, not by the transcript having changed.
        val midTurn = staged.sending || staged.chat.streaming
        _uiState.update {
            it.copy(input = "", attachments = emptyList(), audioNotes = emptyList(), sending = true, sendError = null)
        }
        sendsInFlight++
        awaitingReply = true
        viewModelScope.launch {
            try {
                liftToTitularIfNeeded(sessionId, staged)
                // The answer streams in over the feed while this call is in flight; what it returns is
                // the finished turn, folded in to reconcile anything the feed missed.
                val model = resolveModel(text, attachments.any { it.mime.startsWith("image/") })
                model?.let { rememberModel(sessionId, it) }
                val settled = withContext(ioDispatcher) {
                    repository.sendMessage(
                        sessionId,
                        text,
                        model,
                        files = attachments.map { it.asPromptPart() },
                    )
                }
                _uiState.update { current ->
                    current.copy(chat = settled.fold(current.chat) { state, event -> state.reduce(event) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current ->
                    val reached = if (midTurn) current.chat.hasUserText(text) else current.chat != before
                    if (!reached) {
                        // Never swallow the words — nor the photos, nor the audio notes: everything
                        // goes back in the composer as it was, not as the flattened outgoing text.
                        current.copy(
                            input = input,
                            attachments = attachments,
                            audioNotes = notes,
                            sendError = e.engineFailureKind(),
                        )
                    } else {
                        current
                    }
                }
            } finally {
                // Up while any send is in flight: a second one typed during the turn ends with it.
                sendsInFlight--
                _uiState.update { it.copy(sending = sendsInFlight > 0) }
            }
        }
    }

    /**
     * The first message of a conversation that does not exist yet, from the same screen and
     * composer as an existing one. The session is created with this message as its first prompt and
     * the connectors ticked on its chip, and [MissionChatUiState.started] tells the screen to open
     * it. `sending` stays up until then: the answer is already on its way.
     *
     * A failure puts everything back in the composer, as a failed send does: nothing was created
     * that the person could see, and their words are theirs.
     */
    private fun start(text: String, staged: MissionChatUiState) {
        val attachments = staged.attachments
        _uiState.update {
            it.copy(input = "", attachments = emptyList(), audioNotes = emptyList(), sending = true, sendError = null)
        }
        viewModelScope.launch {
            try {
                val files = attachments.map { it.asPromptPart() }
                val model = resolveModel(text, attachments.any { it.mime.startsWith("image/") })
                val created = withContext(ioDispatcher) {
                    repository.startChat(
                        text = text,
                        // Null while the catalogue is unknown: the repository then takes the defaults.
                        connectors = staged.enabledConnectors?.toList(),
                        model = model,
                        files = files,
                    )
                }
                model?.let { rememberModel(created, it) }
                val title = chatTitle(text).ifBlank { attachments.firstOrNull()?.filename.orEmpty() }
                _uiState.update { it.copy(started = StartedChat(created, title)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        input = staged.input,
                        attachments = attachments,
                        audioNotes = staged.audioNotes,
                        sending = false,
                        sendError = e.engineFailureKind(),
                    )
                }
            }
        }
    }

    /**
     * [model] is the conversation's from now on, on this device (26/10/2026): the chip shows it
     * whatever the scheduler plays into the conversation, and the next messages follow it.
     */
    private fun rememberModel(sessionId: String, model: EngineModelRef) {
        _uiState.update { it.copy(rememberedModel = model) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { conversationModels?.record(sessionId, model) } }
                .onFailure { Logger.w(LOG_TAG, it) { "Could not record the model of $sessionId" } }
        }
    }

    /**
     * The model the next message names: what the chip shows — the pick, else the model this device
     * last sent the conversation with, else the model the last turn ran on, else the conversations'
     * provider default.
     *
     * Never nothing: the engine does not keep a session's model, and a message without one runs on
     * the **agent's** model. A conversation switched to another model went back to the default as
     * soon as the app restarted and forgot the pick (Raphaël, 03/10/2026). The last turn's model is
     * the session's model, and it is sent as such.
     */
    private suspend fun resolveModel(text: String, hasImages: Boolean): EngineModelRef? {
        val effective = _uiState.value.effectiveModel ?: return null
        if (!effective.isAuto) return effective.ref
        // « Auto » (25/10/2026, Raphaël's rule: the model necessary and sufficient). A conversation
        // that already ran keeps its model: the provider's cache is built on it, and changing
        // mid-way throws it away. A new one is read once by the scheduler's trieur. A trieur that
        // does not answer leaves it on the ladder's lowest tier, the cheapest (27/10/2026): sent
        // without a model it ran on the engine's default, Sonnet, which Auto never picks and
        // Raphaël never chose. No ladder either, and only then the engine's default.
        _uiState.value.chat.model?.let { return it }
        val choice = withContext(ioDispatcher) { scheduler?.triage(text, hasImages) }
        val qualified = choice?.model
            ?: ModelTiers(choice?.models.orEmpty()).lowest
            ?: _uiState.value.tiers.lowest
        if (choice?.model == null) {
            Logger.w(LOG_TAG) { "Auto: no answer from the trieur, starting on ${qualified ?: "the engine's default"}" }
        }
        val ref = qualified?.asModelRef() ?: return null
        val picked = _uiState.value.models.firstOrNull { it.modelId == ref.modelId }
            ?: EngineSelectableModel(providerId = ref.providerId, modelId = ref.modelId, label = ref.modelId)
        // On the chip from now on: the person sees what Auto chose, and the next messages follow it.
        _uiState.update { it.copy(model = picked) }
        return picked.ref
    }

    /**
     * « Redo stronger » (25/10/2026): the last request again, on the tier above the model that
     * answered, which the conversation then keeps. The engine has the first answer in its history;
     * the message says what is asked of the stronger model.
     */
    fun redoStronger() {
        val state = _uiState.value
        val sessionId = sessionId ?: return
        if (!state.canRedoStronger) return
        val stronger = state.strongerModel ?: return
        val text = state.lastUserText ?: return
        _uiState.update { it.copy(model = stronger, sending = true, sendError = null) }
        rememberModel(sessionId, stronger.ref)
        sendsInFlight++
        awaitingReply = true
        viewModelScope.launch {
            try {
                val settled = withContext(ioDispatcher) {
                    repository.sendMessage(sessionId, REDO_STRONGER_PREFIX + text, stronger.ref)
                }
                _uiState.update { current ->
                    current.copy(chat = settled.fold(current.chat) { chat, event -> chat.reduce(event) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.engineFailureKind()) }
            } finally {
                sendsInFlight--
                _uiState.update { it.copy(sending = sendsInFlight > 0) }
            }
        }
    }

    /** Stop a reply in progress. The engine ends the run; the feed reports the session going idle. */
    fun stop() {
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.abort(sessionId) } }
        }
    }

    fun retryHistory() {
        if (sessionId == null) return
        _uiState.update { it.copy(loadingHistory = true, historyError = null) }
        loadHistory(trustCache = false)
    }

    /**
     * The whole transcript, on demand, folded under the turns on screen: since 26/10/2026 the
     * reducer puts a message it has not seen at its place in the order of the ids, so the older
     * messages land before the live turn, whose streaming text is kept.
     */
    fun loadEarlierHistory() {
        val sessionId = sessionId ?: return
        if (_uiState.value.loadingEarlier || !_uiState.value.historyTruncated) return
        _uiState.update { it.copy(loadingEarlier = true) }
        viewModelScope.launch {
            try {
                val events = withContext(ioDispatcher) { repository.history(sessionId) }
                historyLimit = null
                _uiState.update { current ->
                    current.copy(
                        chat = events.fold(current.chat) { state, event -> state.reduce(event) },
                        historyTruncated = false,
                        loadingEarlier = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(LOG_TAG, e) { "Earlier history of $sessionId failed" }
                _uiState.update { it.copy(loadingEarlier = false, sendError = e.engineFailureKind()) }
            }
        }
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }
}

private const val LOG_TAG = "Conversation"
private const val HISTORY_ATTEMPTS = 3

/** The device's copy of a tail, a small file: past this, the engine is read without it (27/10/2026). */
private const val CACHE_READ_MS = 2_000L

/** An opening whose step has not moved for this long is rescued ([MissionChatViewModel.watchOpening]). */
private const val OPENING_GUARD_MS = 10_000L

/** How long an opening waits on the connections being dropped before it reads again anyway. */
private const val DROP_CONNECTIONS_MS = 2_000L

/** Rescues per opening: past them, the reads' own errors take over. */
private const val OPENING_RESCUES = 2

/** Messages read at opening: a screenful of history, and the model, context and last request. */
internal const val HISTORY_TAIL = 40

/** A transcript of 40 messages answers in well under this on a live connection; past it, the connection is suspect. */
private const val HISTORY_ATTEMPT_MS = 8_000L

/** Before a read cut by the network is asked again: the time a fresh connection takes to come up. */
private const val HISTORY_RETRY_PAUSE_MS = 500L

/** How long the deltas of a streaming answer are gathered before one state update draws them. */
private const val DELTA_BATCH_MS = 80L

/** How often at most a delta on an unknown part has the transcript read again. */
private const val RECONCILE_PAUSE_MS = 3_000L

/** A user turn carrying exactly these words: a send made mid-turn that reached the engine. */
private fun MissionChatState.hasUserText(text: String): Boolean = turns.any { turn ->
    turn is ChatTurn.User && turn.parts.any { it is ChatPart.Text && it.text.trim() == text.trim() }
}

/** The drawer's name for a new chat: its first line, as the engine titles the session. */
internal fun chatTitle(text: String): String =
    text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(CHAT_TITLE_LENGTH).orEmpty()

private const val CHAT_TITLE_LENGTH = 60

/**
 * What « redo stronger » puts before the person's last words. In the platform's language: the
 * agent's prompts are French, and the message is for the model, not for the screen.
 */
internal const val REDO_STRONGER_PREFIX = "Reprends ma dernière demande avec plus de soin et de rigueur, sur un modèle plus fort :\n\n"
