package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.data.pricing.ModelPriceCache
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.feature.tasks.delegate.CatalogueFetch
import com.garfiec.librechat.feature.tasks.delegate.ComposerStagingDelegate
import com.garfiec.librechat.feature.tasks.delegate.MissionCatalogueDelegate
import com.garfiec.librechat.feature.tasks.util.AudioNote
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.garfiec.librechat.feature.tasks.util.asPromptPart
import com.garfiec.librechat.feature.tasks.util.outgoingMessageText
import com.garfiec.librechat.feature.tasks.util.reduce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * Some audio is at the server's Whisper right now — a dictation about to land in [input], or a
     * deposited file about to join [audioNotes]. One flag for both: Whisper takes one at a time here.
     */
    val transcribing: Boolean = false,
    /** The transcription failed — the one error here that is not the engine's. */
    val transcriptionFailed: Boolean = false,
    /** Why the transcript would not load, or null. */
    val historyError: EngineFailureKind? = null,
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
     * including the many that had never run on it. Null here means « unchanged », which is also what
     * the engine reads from an absent model on the wire.
     */
    val model: EngineSelectableModel? = null,
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
     * Where this transcript was left last time, once the answer is known — see [positionKnown].
     *
     * Null means « never left mid-transcript », and the screen then opens at the tail as it always
     * did. The screen must not restore before this has been read, or it would race the read and
     * land at the bottom anyway.
     */
    val restoredPosition: MissionReadingPosition? = null,
    /** False until the stored position has been read. Distinguishes « none » from « not yet ». */
    val positionKnown: Boolean = false,
    /** A chat or a task (D-077): which agent, which provider, whether connectors are offered. */
    val profile: EngineProfile = EngineProfile.TASK,
    /**
     * The chat provider's own default, shown on the chip of a chat that has not run yet — so a new
     * conversation names the model it is about to use. Never set for a task.
     */
    val defaultModel: EngineSelectableModel? = null,
    /**
     * Whether audio can be turned into words here. False since D-077: the transcription this
     * composer used was LibreChat's, and nothing on the engine side replaces it yet — the mic and
     * the audio entry are then absent rather than present and failing.
     */
    val transcriptionAvailable: Boolean = false,
    /**
     * Set once a **new** chat exists on the engine: the screen hands it to the navigation, which
     * replaces the blank conversation with the real one. Null for an existing session.
     */
    val started: StartedChat? = null,
) {
    /**
     * What the model chip says, and what the picker shows as current.
     *
     * The user's pick for the next message wins; failing that, the model the session's last turn
     * actually ran on — read off the engine's own message envelopes, which is the only place that
     * fact is written. A model the catalogue does not list still gets named, by its raw id: an
     * unfamiliar model is worth showing, and « Session model » in its place says less than nothing.
     */
    val effectiveModel: EngineSelectableModel?
        get() = model ?: chat.model?.let { ref ->
            models.firstOrNull { it.ref == ref }
                ?: EngineSelectableModel(
                    providerId = ref.providerId,
                    modelId = ref.modelId,
                    label = ref.modelId,
                )
        } ?: defaultModel
}

/** A chat the composer just created on the engine, as the navigation needs it. */
data class StartedChat(val sessionId: String, val title: String)

/**
 * What the navigation hands the conversation's view model: which session — none for a chat not
 * started yet — and on which profile. One value rather than two parameters, so the Koin lookup is
 * by this type and cannot confuse a missing session id with something else.
 */
data class MissionChatArgs(
    val sessionId: String?,
    val profile: EngineProfile = EngineProfile.TASK,
)

class MissionChatViewModel(
    /**
     * The session on screen, or null for a chat that does not exist yet (D-077): its first send
     * creates it ([EngineMissionRepository.startChat]) and the navigation then opens the real one.
     */
    private val sessionId: String?,
    private val repository: EngineMissionRepository,
    private val modelPrices: ModelPriceCache,
    private val settings: SettingsDataStore,
    private val positions: MissionReadingPositions,
    /** Null where no transcription service exists — see [MissionChatUiState.transcriptionAvailable]. */
    private val speech: SpeechRepository?,
    private val ioDispatcher: CoroutineDispatcher,
    private val profile: EngineProfile = EngineProfile.TASK,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MissionChatUiState(
            profile = profile,
            transcriptionAvailable = speech != null,
            // Nothing to load for a chat that does not exist yet: no transcript, no saved position.
            loadingHistory = sessionId != null,
            positionKnown = sessionId == null,
        ),
    )
    val uiState: StateFlow<MissionChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    // Declared before `init`, which reads the catalogue: initializers run in textual order.
    private val catalogueLoader = MissionCatalogueDelegate(
        fetchModels = { repository.models(profile) },
        fetchPrices = { modelPrices.prices() },
        fetchConnectors = { repository.connectors() },
        context = ioDispatcher,
    )

    private val staging = ComposerStagingDelegate(
        state = _uiState,
        scope = viewModelScope,
        transcribe = { bytes, mime ->
            speech?.transcribeAudio(bytes, mime) ?: Result.Error()
        },
        context = ioDispatcher,
    )

    init {
        if (sessionId != null) {
            loadHistory()
            openStream()
            viewModelScope.launch {
                val saved = runCatching { positions.positionOf(sessionId) }.getOrNull()
                _uiState.update { it.copy(restoredPosition = saved, positionKnown = true) }
            }
        }
        loadCatalogue()
        viewModelScope.launch {
            settings.chatFontSize.collect { size ->
                _uiState.update { it.copy(fontScale = size.multiplier) }
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
        // A chat is not configured connector by connector (D-077): its perimeter is every connector
        // open to a chat, set at creation. No chip, so no catalogue to fetch for one.
        if (profile == EngineProfile.TASK) loadConnectors()
        loadModels()
    }

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
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            when (val fetched = catalogueLoader.connectors()) {
                is CatalogueFetch.Failed -> _uiState.update { it.copy(connectorsError = fetched.kind) }
                is CatalogueFetch.Loaded -> {
                    val granted = runCatching {
                        withContext(ioDispatcher) { repository.sessionConnectors(sessionId) }
                    }.getOrNull()
                    _uiState.update {
                        it.copy(
                            // Someone is watching this conversation, so nothing is barred as it would
                            // be for an unattended mission (brief §4.2).
                            connectors = fetched.value.offered(),
                            // A tick the user made while this was in flight outranks what the engine
                            // said a moment ago: it has already been sent, and overwriting it here
                            // would undo a checkbox under their finger.
                            enabledConnectors = it.enabledConnectors ?: granted,
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
                            defaultModel = fetched.value.preselected.takeIf { profile == EngineProfile.CHAT },
                        )
                    }
                    // After the models and never instead of them: the price table comes from another
                    // service, and its absence must cost the prices, not the picker.
                    val prices = catalogueLoader.prices()
                    _uiState.update { it.copy(prices = prices) }
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
        val sessionId = sessionId ?: return
        val before = _uiState.value.enabledConnectors.orEmpty()
        val after = if (name in before) before - name else before + name
        _uiState.update { it.copy(enabledConnectors = after) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.setConnectors(sessionId, after.toList()) }
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
     * Seed the conversation from the transcript.
     *
     * The stream is opened alongside rather than after: a mission that is running right now would
     * otherwise have its first tokens dropped while the transcript is being fetched. Both fold into
     * the same state and the reducer is idempotent, so whichever lands first, the result is the same.
     */
    private fun loadHistory() {
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            try {
                val events = withContext(ioDispatcher) { repository.history(sessionId) }
                _uiState.update { current ->
                    // Fold the past *under* whatever the live feed already delivered, so nothing that
                    // arrived while we were fetching is lost.
                    val seeded = events.fold(current.chat) { state, event -> state.reduce(event) }
                    current.copy(chat = seeded, loadingHistory = false, refreshing = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingHistory = false,
                        refreshing = false,
                        historyError = e.engineFailureKind(),
                    )
                }
            }
        }
    }

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
        loadHistory()
    }

    /**
     * Subscribe to the engine's feed. The flow never throws — a dead feed simply ends — so a failure
     * there leaves the transcript on screen instead of tearing the collector down.
     */
    private fun openStream() {
        val sessionId = sessionId ?: return
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repository.events(sessionId)
                .flowOn(ioDispatcher)
                .collect { event ->
                    _uiState.update { it.copy(chat = it.chat.reduce(event)) }
                }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    // The composer's staging — dictation, audio files, photos — lives in ComposerStagingDelegate.

    /** A dictation, transcribed into the composer. */
    fun transcribeAudio(bytes: ByteArray, mime: String) = staging.transcribeAudio(bytes, mime)

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
        if ((text.isEmpty() && attachments.isEmpty()) || staged.sending || staged.started != null) return
        val sessionId = sessionId ?: return startChat(text, staged)
        val input = staged.input
        val notes = staged.audioNotes
        val before = staged.chat
        _uiState.update {
            it.copy(input = "", attachments = emptyList(), audioNotes = emptyList(), sending = true, sendError = null)
        }
        viewModelScope.launch {
            try {
                // The answer streams in over the feed while this call is in flight; what it returns is
                // the finished turn, folded in to reconcile anything the feed missed.
                val model = _uiState.value.model?.ref
                val settled = withContext(ioDispatcher) {
                    repository.sendMessage(
                        sessionId,
                        text,
                        model,
                        files = attachments.map { it.asPromptPart() },
                        profile = profile,
                    )
                }
                _uiState.update { current ->
                    current.copy(chat = settled.fold(current.chat) { state, event -> state.reduce(event) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current ->
                    if (current.chat == before) {
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
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    /**
     * The first message of a chat that does not exist yet (D-077): the session is created on the
     * chat profile with this message as its first prompt, and [MissionChatUiState.started] tells the
     * screen to open it. `sending` stays up until then — the answer is already on its way.
     *
     * A failure puts everything back in the composer, as a failed send does: nothing was created
     * that the person could see, and their words are theirs.
     */
    private fun startChat(text: String, staged: MissionChatUiState) {
        val attachments = staged.attachments
        _uiState.update {
            it.copy(input = "", attachments = emptyList(), audioNotes = emptyList(), sending = true, sendError = null)
        }
        viewModelScope.launch {
            try {
                val created = withContext(ioDispatcher) {
                    repository.startChat(
                        text = text,
                        model = _uiState.value.model?.ref,
                        files = attachments.map { it.asPromptPart() },
                    )
                }
                _uiState.update { it.copy(started = StartedChat(created, chatTitle(text))) }
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
        loadHistory()
    }

    /**
     * Records where the reader is, so the next visit opens there.
     *
     * Fire-and-forget on purpose: this is called as the list scrolls, and a failed write costs the
     * accuracy of one position, never the scroll. `runCatching` rather than a `try` inside the
     * launch because a store that cannot be written must not tear the screen's scope down.
     */
    fun rememberPosition(index: Int, offset: Int) {
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            runCatching { positions.remember(sessionId, MissionReadingPosition(index, offset)) }
        }
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }
}

/** The drawer's name for a new chat: its first line, as the engine titles the session. */
internal fun chatTitle(text: String): String =
    text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(CHAT_TITLE_LENGTH).orEmpty()

private const val CHAT_TITLE_LENGTH = 60
