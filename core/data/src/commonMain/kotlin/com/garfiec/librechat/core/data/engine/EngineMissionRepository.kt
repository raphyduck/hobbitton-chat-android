package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.di.ioDispatcher
import com.garfiec.librechat.core.data.datastore.GlobalProfileSource
import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineProviderModel
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.engineHistoryEvents
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.SchedulerRun
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.engine.EngineEventTransport
import com.garfiec.librechat.core.network.engine.EngineHttpException
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * One conversation as the lists show it. Since D-082 (12/10/2026) every session opens the same
 * way, whoever started it: a chat, a task of an earlier version, or a run of a scheduled mission.
 *
 * [scheduled] marks a run the scheduler started: it gets a small clock, and its [title] is the
 * mission's name rather than the scheduler's `<mission> — <due minute>` (the age at the end of the
 * row already says when). [running] claims only what the status map proves: a session that is
 * answering right now. [unread]: it moved since this device last showed it, and, for a scheduled
 * run, it has something to say (16/10/2026, [ChatScope]).
 */
data class EngineChatSummary(
    val sessionId: String,
    val title: String,
    val lastActivityMillis: Long?,
    val running: Boolean,
    val kind: EngineSessionKind = EngineSessionKind.CHAT,
    val scheduled: Boolean = false,
    val unread: Boolean = false,
)

/**
 * Which sessions a list holds (16/10/2026, Raphaël's rule). Since 19/10/2026 the drawer shows the
 * Conversations page's [ALL] too: the two lists show the same sessions; [MANUAL] is unused.
 *
 *  * [MANUAL], the drawer: the conversations the person started, never a run of the scheduler —
 *    a mission run every half hour would bury them within a morning.
 *  * [ALL], the Conversations page: every session, unread first, then by latest activity
 *    ([inboxOrder]).
 */
enum class ChatScope { MANUAL, ALL }

/**
 * The Conversations page's order: the unread first, then the most recently active, in each half.
 */
fun inboxOrder(chats: List<EngineChatSummary>): List<EngineChatSummary> =
    chats.sortedWith(
        compareByDescending<EngineChatSummary> { it.unread }
            .thenByDescending { it.lastActivityMillis ?: Long.MIN_VALUE },
    )

/**
 * Whether a scheduler run has something to say, and so may count as unread (16/10/2026): it said
 * so ([SchedulerRun.hasNews]), it failed, or it stopped short. A run that found nothing, one still
 * running, and one the scheduler kept no verdict for (recorded before it did, or not its own) are
 * read: a list that lit up for every half-hourly pass would point at nothing.
 */
fun runHasNews(run: SchedulerRun?): Boolean =
    run != null && (run.hasNews == true || run.succeeded == false || run.incomplete)

/**
 * Whether a run stays out of the conversation lists (19/10/2026): its mission is discreet (a
 * background mission, which speaks through a report or a decision conversation), failed or not
 * (Raphaël's rule, 20/10/2026: traiter-les-non-lus, failing on a spent budget, filled the drawer;
 * its failure stays in the mission's history under Programmées, whose row turns red).
 * [discreetMissions] names the missions the scheduler marks discreet, read off its latest runs: a
 * session whose own run fell outside that window is still known by its mission.
 */
fun runHidden(mission: String?, discreetMissions: Set<String>): Boolean =
    mission != null && mission in discreetMissions

/**
 * Whether a session that moved since this device showed it counts as unread: a conversation always,
 * a run when it has something to say ([runHasNews]). A discreet mission's run is not listed at all
 * ([runHidden]).
 */
fun runUnread(mission: String?, run: SchedulerRun?): Boolean = mission == null || runHasNews(run)

/**
 * What the model picker needs: the list, and the model a conversation runs on when none is picked
 * ([preselected]: the person's default from Settings when the list still offers it, the engine's
 * own otherwise, which [engineDefault] keeps for Settings to name).
 */
data class EngineModelChoice(
    val models: List<EngineSelectableModel> = emptyList(),
    val preselected: EngineSelectableModel? = null,
    val engineDefault: EngineSelectableModel? = null,
)

/**
 * The model a new conversation starts on, as the person set it in Settings (19/10/2026), by its
 * model id on the conversations' provider; null to leave the engine's default.
 */
fun interface ModelPreference {
    suspend fun modelId(): String?
}

/**
 * The app's conversations on the engine: the drawer's list, starting one, its turns and its feed.
 *
 * **Nothing is cached locally.** The engine is the source of truth: a mission that exists only on a
 * phone is a mission nobody can supervise — not from the web, not from the scheduler, not from
 * another device. That is also why the list is rebuilt on each refresh rather than merged into a
 * local store.
 */
class EngineMissionRepository(
    private val api: AgentEngineApi,
    private val scheduler: SchedulerApi,
    private val streamClient: EngineStreamClient,
    private val eventTransport: EngineEventTransport,
    private val globalProfile: GlobalProfileSource,
    /** Which session is a chat and which a task, as far as this device knows (D-077). */
    private val kinds: SessionKindStore,
    /** When this device last showed each conversation; null leaves every row read (16/10/2026). */
    private val reads: ConversationReads? = null,
    /** The person's default model (Settings, 19/10/2026); null leaves the engine's. */
    private val modelPreference: ModelPreference? = null,
) {

    /**
     * The global profile's instructions, as the engine's `system`, or null when there is nothing
     * to say.
     *
     * **Read here rather than passed in, on purpose.** Every route that puts words in front of a
     * model goes through this class, so reading the profile here is what makes « the instructions
     * apply everywhere » structural instead of a convention each caller must remember. A ViewModel
     * that forgets to thread it would silently produce a mission that never heard its owner — the
     * failure would look like the model ignoring the instructions, which is a much longer thing to
     * diagnose than a compile error.
     */
    private suspend fun instructions(): String? =
        globalProfile.current().takeIf { it.enabled }?.instructions?.takeIf { it.isNotBlank() }

    /**
     * The models a conversation may run on, and the one it runs on when none is picked: the
     * person's default ([modelPreference]) while the deployment still lists it, else the engine's.
     *
     * **Only the conversations' provider** (`hobbitton-chat`, D-082), and only as the deployment
     * declared it (`source == "config"`). The engine also carries the endpoint OpenCode ships with,
     * keyed and ready, and a turn sent there would leave the platform's gateway entirely: no cost
     * accounting, no ceiling, and none of the catalogue curated for this deployment (D-054). The
     * gateway's own provider lists the same models on the scheduled missions' budget.
     *
     * One call, both answers. Asking twice — once for the list, once for the default — would be two
     * round trips for one sheet, and two chances for them to disagree if the engine is reconfigured
     * in between.
     *
     * Sorted by label, because a map promises no order and a picker that reshuffles between two
     * openings is a picker that gets misread.
     */
    suspend fun models(): EngineModelChoice {
        val catalogue = api.providers()
        val declared = catalogue.providers.filter { it.source == DECLARED_PROVIDER && it.id == CHAT_PROVIDER }

        fun selectable(providerId: String, key: String, model: EngineProviderModel?) =
            EngineSelectableModel(
                providerId = providerId,
                modelId = model?.id ?: key,
                label = model?.name?.takeIf { it.isNotBlank() } ?: (model?.id ?: key),
                // Declared modalities without « image » mean blind; nothing declared means unknown,
                // taken as seeing: a warning on every model would be a warning on none.
                seesImages = model?.modalities?.input?.let { IMAGE_MODALITY in it } ?: true,
            )

        val models = declared
            .flatMap { p -> p.models.map { (key, m) -> selectable(p.id, key, m) } }
            .sortedBy { it.label }
        // Null when the engine names none — and then nothing is preselected, rather than a
        // first-in-the-list guess quietly becoming this deployment's default.
        val engineDefault = declared.firstNotNullOfOrNull { p ->
            catalogue.default[p.id]?.let { key -> selectable(p.id, key, p.models[key]) }
        }
        // A preference the deployment no longer lists falls back to the engine's: a turn sent to a
        // model the gateway dropped would only fail.
        val preferred = modelPreference?.modelId()?.let { id -> models.firstOrNull { it.modelId == id } }
        return EngineModelChoice(models = models, preselected = preferred ?: engineDefault, engineDefault = engineDefault)
    }

    /**
     * The conversations of [scope], most recently active first for the drawer ([ChatScope.MANUAL]:
     * the scheduler's runs left out), unread first for the Conversations page ([ChatScope.ALL],
     * [inboxOrder]). A run is marked ([EngineChatSummary.scheduled]) and named after its mission.
     *
     * Classified by [classifySession]: what this device recorded first, then the scheduler's title
     * shape, and only then the transcript — read for at most [MAX_CLASSIFICATION_READS] unknown
     * sessions per refresh, and the verdict recorded so it is never read again. A session that says
     * nothing yet (no message) is left out: it is either a conversation being created right now,
     * which its creator recorded, or nobody's.
     *
     * Unread ([EngineChatSummary.unread]) costs three reads per refresh, whatever the number of
     * rows: this device's read marks, the scheduler's (a conversation whose question it withdrew,
     * 22/10/2026), and, for [ChatScope.ALL], the scheduler's latest runs, which say whether each
     * had something to say ([runHasNews]). Without this device's marks, rows read as read.
     */
    suspend fun recentChats(
        scope: ChatScope = ChatScope.MANUAL,
        limit: Int = if (scope == ChatScope.ALL) CONVERSATIONS_SHOWN else RECENT_CHATS_SHOWN,
        // The session search (21/10/2026): over EVERY session the engine lists, not over the rows
        // already drawn, accents folded ([matchesSearch]). Blank lists as before.
        query: String = "",
    ): List<EngineChatSummary> {
        val statuses = runCatching { api.status() }.getOrDefault(emptyMap())
        val recorded = recordedKinds()
        val read = reads?.let { store -> runCatching { store.snapshot() }.getOrNull() }
            ?.withServerReads(serverReads())
        val runs = if (scope == ChatScope.ALL) latestRuns() else emptyMap()
        val discreetMissions = runs.values.filter { it.discreet }.mapNotNull { it.mission }.toSet()
        val sessions = api.sessions()
            .sortedByDescending { it.time?.updated ?: it.time?.created ?: Long.MIN_VALUE }
        val learned = mutableMapOf<String, EngineSessionKind>()
        var classified = 0
        val chats = mutableListOf<EngineChatSummary>()
        val searching = query.isNotBlank()
        val reads = if (searching) MAX_SEARCH_CLASSIFICATION_READS else MAX_CLASSIFICATION_READS
        for (session in sessions) {
            if (chats.size >= limit) break
            val mission = session.title?.let(::scheduledMissionName) ?: runs[session.id]?.mission
            val title = mission?.let(::missionDisplayName) ?: session.title.orEmpty().ifBlank { session.id }
            // Before any transcript is read: a run never costs the drawer one, nor does a session
            // the search does not name.
            val left = (mission != null && scope == ChatScope.MANUAL) ||
                runHidden(mission, discreetMissions) ||
                (searching && !title.matchesSearch(query))
            if (left) continue
            var kind = classifySession(session.title, recorded[session.id], messages = null)
            if (kind == null && classified < reads) {
                classified++
                val messages = runCatching { api.messages(session.id) }.getOrNull()
                kind = classifySession(session.title, null, messages)
                if (kind != null) learned[session.id] = kind
            }
            if (kind != null) {
                val active = statuses[session.id]
                val lastActivity = session.time?.updated ?: session.time?.created
                val moved = read?.isUnread(session.id, lastActivity) == true
                chats += EngineChatSummary(
                    sessionId = session.id,
                    title = title,
                    lastActivityMillis = lastActivity,
                    running = active != null && active.type != IDLE_STATUS,
                    kind = kind,
                    scheduled = mission != null,
                    unread = moved && runUnread(mission, runs[session.id]),
                )
            }
        }
        remember(learned)
        return if (scope == ChatScope.ALL) inboxOrder(chats) else chats
    }

    /** The conversation was on screen at [atMillis]: it reads as read up to then. */
    suspend fun markRead(sessionId: String, atMillis: Long) {
        val store = reads ?: return
        runCatching { store.markRead(sessionId, atMillis) }
            .onFailure { Logger.w(it) { "Could not record that $sessionId was read" } }
    }

    /**
     * Each session's latest run, by session id. A retry continues the same session (11/10/2026), so
     * a session may carry several runs: the newest, listed first, is the one that says how it ended.
     * Empty when the scheduler cannot say: every run then reads as read, never as news.
     */
    /** The scheduler's read marks ([SchedulerApi.serverReads]); none when it does not answer. */
    private suspend fun serverReads(): Map<String, Long> =
        runCatching { scheduler.serverReads() }
            .onFailure { Logger.w(it) { "Conversations: the scheduler's read marks are unavailable" } }
            .getOrDefault(emptyMap())

    private suspend fun latestRuns(): Map<String, SchedulerRun> {
        val runs = runCatching { scheduler.runs() }
            .onFailure { Logger.w(it) { "Conversations: the scheduler's runs are unavailable" } }
            .getOrDefault(emptyList())
        val bySession = LinkedHashMap<String, SchedulerRun>()
        runs.forEach { run -> run.session?.let { bySession.getOrPut(it) { run } } }
        return bySession
    }

    /**
     * Starts a conversation: an engine session on the `chat` profile, then its first message.
     *
     * Since D-082 (12/10/2026) this is the only way the app starts one: chats and tasks are one
     * conversation, as on Claude.ai, and the `chat` profile carries what the tasks' did:
     *
     *  * the session stands at the titulaire's trust level (D-083, 22/10/2026): the scheduler,
     *    not the app, decides what it reaches through the annuaire. Of [connectors], only the
     *    shell still counts, the one thing that level leaves on demand. The level is recorded
     *    before the first prompt (D-071; a recording that fails is a start that fails), and the
     *    direct connectors of [ConnectorCatalogue.titularPerimeter] become the session's rules
     *    ([permissionsFor]);
     *  * the model comes from `hobbitton-chat` ([forChat]), which carries the conversations'
     *    budget (its default when none is named).
     *
     * [model] travels with the **prompt**, never with the session: since OpenCode 1.18.18
     * `POST /session` rejects a `model` key outright (HTTP 400, naming no field).
     *
     * The kind is recorded as soon as the session exists, before anything can fail: a conversation
     * whose first prompt was lost still has a place in the drawer.
     */
    suspend fun startChat(
        text: String,
        connectors: List<String>?,
        model: EngineModelRef? = null,
        files: List<EnginePromptPart> = emptyList(),
    ): String {
        val catalogue = connectors()
        // D-083 (22/10/2026): the session stands at the titulaire's level and the scheduler
        // decides what that reaches. Of the ticks, only the shell still means something.
        val shell = connectors?.contains(SHELL_CONNECTOR) == true
        val perimeter = catalogue.titularPerimeter(shell)
        val session = api.createSession(
            CreateEngineSessionRequest(
                agent = CHAT_AGENT,
                title = text.trim().ifBlank { files.firstOrNull()?.filename.orEmpty() }.take(TITLE_LENGTH),
                permission = permissionsFor(catalogue, perimeter),
            ),
        )
        runCatching { kinds.record(session.id, EngineSessionKind.CHAT) }
            .onFailure { Logger.w(it) { "Could not record a new chat's kind" } }
        scheduler.setScope(session.id, shellTick(shell), level = TITULAR_LEVEL)
        api.prompt(
            sessionId = session.id,
            request = EnginePromptRequest(
                // Files first, text last: the order the classic send uses, and OpenCode's own UI.
                parts = files + listOfNotNull(text.takeIf { it.isNotBlank() }?.let { EnginePromptPart.text(it) }),
                agent = CHAT_AGENT,
                model = chatModel(model),
                system = instructions(),
            ),
        )
        return session.id
    }

    /**
     * The model a turn runs on: the one asked for, moved onto the conversations' provider, or that
     * provider's own default. Named whenever the catalogue answers: an absent model would leave
     * the choice to the engine, and the budget is not something to leave to it.
     */
    private suspend fun chatModel(requested: EngineModelRef?): EngineModelRef? =
        requested?.forChat() ?: runCatching { models().preselected?.ref }.getOrNull()

    private suspend fun recordedKinds(): Map<String, EngineSessionKind> =
        runCatching { kinds.all() }.getOrDefault(emptyMap())

    private suspend fun remember(learned: Map<String, EngineSessionKind>) {
        if (learned.isEmpty()) return
        runCatching { kinds.recordAll(learned) }
            .onFailure { Logger.w(it) { "Could not record the kinds of ${learned.size} session(s)" } }
    }

    suspend fun abort(sessionId: String) = api.abort(sessionId)

    /**
     * What the session has already said, replayed as the events a live turn would have produced.
     *
     * The transcript is the **only** place a mission's past lives: the classic routes are what
     * launched it, and the v2 durable feed knows nothing about such a session. Seeding from here and
     * tailing [events] afterwards is what makes the chat open on a conversation instead of a blank
     * page — the bug the tab shipped with on 29/08/2026.
     */
    suspend fun history(sessionId: String): List<EngineStreamEvent> {
        return engineHistoryEvents(api.messages(sessionId))
    }

    /**
     * What is happening in the session right now. The engine's feed is global; the client keeps only
     * this session's frames.
     *
     * Read and parsed on the IO dispatcher whoever collects it (20/10/2026). The feed carries every
     * session's frames, and a frame repeats a tool's whole output each time it grows (60 000
     * characters since the scheduler's ceiling went up): parsed on the main thread, a scheduled run
     * streaming its tools froze the app until it was killed, the conversation on screen left on its
     * placeholders. The collector's own work (a state update, a chime) stays where it collects.
     */
    fun events(sessionId: String): Flow<EngineStreamEvent> =
        streamClient.connect(sessionId, eventTransport).flowOn(ioDispatcher)

    /**
     * Every session's events, each naming its session: what the attention watcher listens to, so a
     * question asked in a conversation nobody has open still reaches the person. Off the main
     * thread like [events]: the watcher collects it from the shell's scope, which is the main one.
     */
    fun allEvents(): Flow<EngineStreamClient.SessionEvent> =
        streamClient.connectAll(eventTransport).flowOn(ioDispatcher)

    /**
     * The questions this session's agent is waiting on. The feed announces each one once; a screen
     * opened after it (a new chat whose first turn asked straight away, a feed that dropped) reads
     * it here, or the turn stays blocked behind a form nobody sees.
     */
    suspend fun pendingQuestions(sessionId: String): List<EngineQuestionRequest> =
        allPendingQuestions().filter { it.sessionId == sessionId }

    /** Every question waiting on the engine, all sessions: the attention watcher's catch-up. */
    suspend fun allPendingQuestions(): List<EngineQuestionRequest> = api.pendingQuestions()

    /**
     * Answers a question; the agent's turn resumes. One list of labels per question, in order.
     *
     * A question that is no longer pending (answered from another device, or its turn stopped)
     * answers 404; that is not a failure to show, the form simply has nothing left to answer.
     */
    suspend fun answerQuestion(requestId: String, answers: List<List<String>>) =
        unlessGone { api.replyQuestion(requestId, answers) }

    /** Dismisses a question: the agent carries on without the answer. A 404 is ignored as above. */
    suspend fun dismissQuestion(requestId: String) = unlessGone { api.rejectQuestion(requestId) }

    private suspend fun unlessGone(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: EngineHttpException) {
            if (e.status != HTTP_NOT_FOUND) throw e
        }
    }

    /** A session's title as the engine holds it, for a notification; null when it cannot be read. */
    suspend fun sessionTitle(sessionId: String): String? =
        runCatching { api.session(sessionId).title }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Sends a message and waits for the finished answer. The turn also streams on [events] while this
     * call is in flight, so the screen fills in token by token and this return value is the
     * reconciliation rather than the first thing the user sees.
     */
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        model: EngineModelRef? = null,
        files: List<EnginePromptPart> = emptyList(),
    ): List<EngineStreamEvent> {
        // EVERY turn names its agent and its provider. A turn that names no agent does NOT stay on
        // the session's: the engine runs it on its default (a task's follow-up ran on `build`, with
        // none of the session's rules, on 29/09/2026; the server's default is a profile with no
        // tool since D-082). Since D-082 every conversation's turn is a `chat` turn (an old task,
        // a scheduled run answered from the phone), on the conversations' budget; the session's
        // own rules still decide what it may do.
        return engineHistoryEvents(
            listOf(
                api.sendMessage(
                    sessionId,
                    text,
                    model = chatModel(model),
                    files = files,
                    system = instructions(),
                    agent = CHAT_AGENT,
                ),
            ),
        )
    }

    /**
     * The connectors a mission may be given, as the scheduler declares them.
     *
     * Cached for the process: the catalogue is a constant of the deployment, it changes when the
     * platform is reconfigured and not while a phone is open. Fetching it per picker opening would
     * be a round trip for an answer that cannot have changed — and one more thing to fail while
     * someone is mid-tick.
     */
    suspend fun connectors(): ConnectorCatalogue =
        cachedConnectors ?: scheduler.connectors().also { cachedConnectors = it }

    /**
     * Which connectors a **live** session currently carries, read off the engine.
     *
     * The screen had no way of knowing: it started from an empty set and only ever learned what the
     * user ticked in front of it, so a mission launched with nine connectors — by the scheduler, or
     * from the New-mission sheet — opened its conversation labelled « No connector » while its own
     * transcript showed it reading mail. Reported 30/08/2026.
     *
     * The reading is deliberately strict: a connector counts as granted only when **every** tool it
     * declares is allowed. [permissionsFor] writes exactly that — one `allow` rule per tool — so a
     * ruleset this app or the scheduler produced round-trips exactly; a hand-written one that opens
     * half a connector reads as off, which understates rather than overstates what the session can do.
     */
    suspend fun sessionConnectors(sessionId: String): Set<String> {
        val catalogue = connectors()
        val declared = connectorsGranted(catalogue, api.session(sessionId).permission)
        // The annuaire's share of a session lives in the scheduler, not in the rules (D-071). A
        // session the app never recorded — the scheduler's own, or one launched before D-071 —
        // has no scope, and its chips show what its rules carry. A scheduler that cannot be
        // reached reads the same way: the chips understate rather than invent, which is the only
        // direction a capability chip may err in.
        val scoped = runCatching { scheduler.scope(sessionId) }.getOrNull().orEmpty()
        return declared + scoped.filter { it in catalogue.connecteurs }
    }

    /**
     * Re-grants a **live** session at the titulaire's level, replacing its whole ruleset: the shell
     * opened when [connectors] holds it, closed otherwise (D-083, 22/10/2026; the composer's only
     * choice left). A scheduled run opened from the app is lifted to the titulaire's level by it:
     * the person is now the one speaking to it.
     */
    suspend fun setConnectors(sessionId: String, connectors: List<String>) {
        // The scope first (D-071): if recording it fails, nothing has changed and the caller hears
        // it; recording it after the rules would leave a session whose direct tools moved while
        // the annuaire still serves yesterday's list.
        val shell = SHELL_CONNECTOR in connectors
        scheduler.setScope(sessionId, shellTick(shell), level = TITULAR_LEVEL)
        // Never autonomous here: someone is looking at the screen, which is the whole premise of
        // §4.2's ban — an approval prompt nobody answers is not a safeguard, but one they *do*
        // answer is exactly the supervision the rule asks for.
        val catalogue = connectors()
        api.setPermissions(sessionId, permissionsFor(catalogue, catalogue.titularPerimeter(shell)))
    }

    /** What the titulaire's level is asked for on top of what it reaches: the shell, or nothing. */
    private fun shellTick(shell: Boolean): List<String> = if (shell) listOf(SHELL_CONNECTOR) else emptyList()

    private var cachedConnectors: ConnectorCatalogue? = null

    private companion object {
        const val TITLE_LENGTH = 60

        /** The engine's answer about a question that is no longer pending. */
        const val HTTP_NOT_FOUND = 404

        /** The engine's word for a session that is doing nothing; any other status is work. */
        const val IDLE_STATUS = "idle"

        /**
         * The drawer's conversations: a few weeks of them, not an archive. The scheduler's runs
         * left it on 16/10/2026; the Conversations page lists them ([CONVERSATIONS_SHOWN]).
         */
        const val RECENT_CHATS_SHOWN = 60

        /**
         * The Conversations page: the runs too, about fifty a day once a mission passes every half
         * hour, so a few days of them. The unread are sorted first among these.
         */
        const val CONVERSATIONS_SHOWN = 200

        /**
         * Transcripts read per refresh to classify sessions this device has never seen. Each
         * verdict is recorded, so a backlog drains over a few openings instead of one slow one.
         */
        const val MAX_CLASSIFICATION_READS = 10

        /**
         * The same, for a search (21/10/2026): it reaches old sessions this device may never have
         * shown, and its matches are few, so it may read more of them at once.
         */
        const val MAX_SEARCH_CLASSIFICATION_READS = 40

        /**
         * `source` of a provider this deployment declared in its own `opencode.json`, as opposed
         * to `custom` for one OpenCode ships with. Measured against the live engine on 28/08/2026:
         * `hobbitton-gateway` reports `config`, OpenCode's own bundled endpoint reports `custom`.
         */
        const val DECLARED_PROVIDER = "config"

        /** The input modality a model declares when it sees images (`opencode.json`, `modalities.input`). */
        private const val IMAGE_MODALITY = "image"
    }
}
