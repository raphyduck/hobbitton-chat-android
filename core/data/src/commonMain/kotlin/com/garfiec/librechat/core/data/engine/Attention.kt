package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the platform does to get the person's attention: a short sound, and system notifications.
 * Android's lives in `:app` (`AndroidAttentionNotifier`), next to the strings it shows.
 *
 * The decision of *when* is not here but in [AttentionSignals], which is common and tested: the
 * platform only knows how.
 */
interface AttentionNotifier {
    /** True while one of the app's screens is visible to the person. */
    val inForeground: Boolean

    /** The notification sound, played once, for something the person is already looking at. */
    fun chime()

    /** « Butler is asking you something », opening the conversation when tapped. Sounds on its channel. */
    fun showQuestion(conversation: OpenConversation, requestId: String, question: String)

    /** « The reply is ready », for a turn the person was waiting on. */
    fun showReplyReady(conversation: OpenConversation)

    fun cancelQuestion(requestId: String)

    fun cancelReplyReady(sessionId: String)
}

/** A conversation a notification opens: enough for the navigation to pick the right screen. */
data class OpenConversation(
    val sessionId: String,
    val title: String?,
    /** Null for a session this device never recorded: it opens as a task, the general case. */
    val kind: EngineSessionKind?,
)

/** Whether the person wants to be signalled at all: the Settings switch. */
fun interface AttentionPreference {
    suspend fun enabled(): Boolean
}

/**
 * When to make a sound and when to post a notification, for the two moments a conversation needs
 * the person (asked for on 03/10/2026, after Claude's own app):
 *
 *  * **a question**: the agent's turn is blocked on an answer, and stays blocked until somebody
 *    gives one. Looking at that very conversation: a chime, the form is already under their thumb.
 *    Anywhere else, or the app in the background: a notification that opens it.
 *  * **a reply ready**: a turn the person started (or watched start) has finished while they were
 *    not looking at it. Looking at it: nothing, they saw it end.
 *
 * Nothing at all when the switch is off. The notifications themselves are the platform's
 * ([AttentionNotifier]); this class holds no Android and is pinned by `AttentionSignalsTest`.
 */
class AttentionSignals(
    private val notifier: AttentionNotifier,
    private val preference: AttentionPreference,
) {
    private val _onScreen = MutableStateFlow<String?>(null)

    /** The conversation the person is looking at, or null; set by the conversation screen. */
    val onScreen: StateFlow<String?> = _onScreen.asStateFlow()

    /**
     * Each question is signalled once per process. The engine announces it on the feed, and the
     * watcher also reads the pending list when it (re)starts; without this, every restart of the
     * watcher (a sign-in, a recreated activity) would ring again for the same question.
     */
    private val signalled = mutableSetOf<String>()
    private val signalledLock = Mutex()

    fun enter(sessionId: String) {
        _onScreen.value = sessionId
        // The reply they were told about is in front of them now.
        notifier.cancelReplyReady(sessionId)
    }

    fun leave(sessionId: String) {
        _onScreen.update { if (it == sessionId) null else it }
    }

    private fun looking(sessionId: String) = notifier.inForeground && _onScreen.value == sessionId

    suspend fun questionAsked(request: EngineQuestionRequest, conversation: suspend () -> OpenConversation) {
        if (!signalledLock.withLock { signalled.add(request.id) }) return
        if (!preference.enabled()) return
        if (looking(request.sessionId)) {
            notifier.chime()
        } else {
            val first = request.questions.firstOrNull()
            notifier.showQuestion(
                conversation = conversation(),
                requestId = request.id,
                question = first?.question?.takeIf { it.isNotBlank() } ?: first?.header.orEmpty(),
            )
        }
    }

    /** Answered or dismissed, here or elsewhere: its notification has nothing left to ask. */
    fun questionClosed(requestId: String) {
        notifier.cancelQuestion(requestId)
    }

    suspend fun replyReady(sessionId: String, conversation: suspend () -> OpenConversation) {
        if (looking(sessionId)) return
        if (!preference.enabled()) return
        notifier.showReplyReady(conversation())
    }
}

/**
 * Listens to the whole engine for questions: the one signal that cannot wait for a screen to be
 * open. A task launched and left behind blocks on its question until somebody answers; the
 * conversation screen would only see it once opened.
 *
 * The scheduler's own missions never ask: their sessions are built without the `question` tool
 * (`construire_permissions`, server side), so every question on this feed is one somebody started
 * from the app.
 *
 * Run by the shell while signed in ([watch] suspends until cancelled). The feed reconnects on its
 * own; this only folds what it says.
 */
class EngineAttentionWatcher(
    private val repository: EngineMissionRepository,
    private val signals: AttentionSignals,
    private val events: () -> Flow<EngineStreamClient.SessionEvent> = repository::allEvents,
    private val pending: suspend () -> List<EngineQuestionRequest> = repository::allPendingQuestions,
) {
    suspend fun watch() {
        // What was asked while nobody was listening (the app closed, the phone asleep). Read once
        // at start; the feed takes over from there. A failure costs this catch-up, not the watch.
        try {
            pending().forEach { signals.questionAsked(it) { conversationOf(it.sessionId) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Attention: the pending questions could not be read" }
        }
        events().collect { (sessionId, event) ->
            when (event) {
                is EngineStreamEvent.QuestionAsked -> signals.questionAsked(event.request) { conversationOf(sessionId) }
                is EngineStreamEvent.QuestionClosed -> signals.questionClosed(event.requestId)
                else -> Unit
            }
        }
    }

    private suspend fun conversationOf(sessionId: String) = OpenConversation(
        sessionId = sessionId,
        title = repository.sessionTitle(sessionId),
        kind = repository.recordedKind(sessionId),
    )
}

/**
 * A conversation a notification asked to open, waiting for the shell to open it. The activity
 * deposits it from the notification's intent; the shell takes it once signed in.
 */
class ConversationRequests {
    private val _pending = MutableStateFlow<OpenConversation?>(null)
    val pending: StateFlow<OpenConversation?> = _pending.asStateFlow()

    fun open(conversation: OpenConversation) {
        _pending.value = conversation
    }

    /** Takes the request, so a recomposition never opens it twice. */
    fun consume(conversation: OpenConversation) {
        _pending.update { if (it == conversation) null else it }
    }
}
