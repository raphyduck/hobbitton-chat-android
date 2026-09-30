package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.GlobalProfileSource
import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineProviderModel
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.engine.engineHistoryEvents
import com.garfiec.librechat.core.model.engine.judgeMission
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.engine.EngineEventTransport
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One mission as the tab shows it: what it is, and what it is doing.
 *
 * [state] is not a field the engine returns — it is decided by `judgeMission`, because the engine
 * has no such notion and the obvious reading of what it *does* return hides failures.
 */
data class Mission(
    val sessionId: String,
    val title: String,
    val state: MissionState,
    /**
     * When this mission last said something — the timestamp the list is sorted and stamped by.
     *
     * **Not the creation date**, which is what the tab used until 31/08/2026 and which put a
     * mission that answered five minutes ago below one launched an hour earlier and silent since.
     * A conversation list is read the way a messaging app's is: the one that just moved goes on top.
     */
    val lastActivityMillis: Long?,
)

/**
 * One chat as the drawer lists it (D-077): an engine session run on the `chat` profile.
 *
 * [running] claims only what the status map proves, like the drawer's mission rows did: a chat
 * that is answering right now. Nothing else is read to build a row.
 */
data class EngineChatSummary(
    val sessionId: String,
    val title: String,
    val lastActivityMillis: Long?,
    val running: Boolean,
)

/**
 * What the New-mission sheet needs to offer a model: the list, and what to tick when it opens.
 */
data class EngineModelChoice(
    val models: List<EngineSelectableModel> = emptyList(),
    val preselected: EngineSelectableModel? = null,
)

/**
 * The missions of the Tasks tab, read from the engine and judged here.
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
     * The models a mission may be launched on, and the one the engine would pick itself.
     *
     * **Only providers the deployment declared itself** (`source == "config"`). The engine also
     * carries the endpoint OpenCode ships with, keyed and ready — and a mission sent there would
     * leave the platform's gateway entirely: no cost accounting, no ceiling, and none of the
     * catalogue curated for this deployment. The brief hands the model choice to the user (§6bis);
     * it does not hand out a way around the gateway. Server-side D-054 records the choice and its
     * alternative.
     *
     * One call, both answers. Asking twice — once for the list, once for the default — would be two
     * round trips for one sheet, and two chances for them to disagree if the engine is reconfigured
     * in between.
     *
     * Sorted by label, because a map promises no order and a picker that reshuffles between two
     * openings is a picker that gets misread.
     */
    suspend fun models(profile: EngineProfile = EngineProfile.TASK): EngineModelChoice {
        val catalogue = api.providers()
        // Declared by the deployment, AND meant for this profile (D-077): a chat is offered the
        // chat's provider only, a task everything declared but that one — see [offersProvider].
        val declared = catalogue.providers.filter { it.source == DECLARED_PROVIDER && offersProvider(profile, it.id) }

        fun selectable(providerId: String, key: String, model: EngineProviderModel?) =
            EngineSelectableModel(
                providerId = providerId,
                modelId = model?.id ?: key,
                label = model?.name?.takeIf { it.isNotBlank() } ?: (model?.id ?: key),
            )

        return EngineModelChoice(
            models = declared
                .flatMap { p -> p.models.map { (key, m) -> selectable(p.id, key, m) } }
                .sortedBy { it.label },
            // Null when the engine names none — and then nothing is preselected, rather than a
            // first-in-the-list guess quietly becoming this deployment's default.
            preselected = declared.firstNotNullOfOrNull { p ->
                catalogue.default[p.id]?.let { key -> selectable(p.id, key, p.models[key]) }
            },
        )
    }

    /**
     * The [limit] most recently active sessions, each judged — the Tasks tab's list under its
     * schedule (asked for on 25/09/2026, after a day of the tab showing only what was running).
     *
     * Capped **before** the transcripts are fetched: judging a session costs its whole transcript,
     * and the scheduler adds about nine sessions a day. A running session needs no transcript at
     * all, so the cap bounds the settled ones only in practice.
     */
    suspend fun recentMissions(limit: Int = RECENT_SHOWN): List<Mission> {
        val statuses = api.status()
        // Chats are not tasks (D-077): they live in the drawer, and the tab lists missions only.
        // What this device already knows is dropped before the cap, so a morning of chatting does
        // not push the night's missions off the list; the rest is sorted out once its transcript
        // is read, below.
        val recorded = recordedKinds()
        val recent = api.sessions()
            .filter { recorded[it.id] != EngineSessionKind.CHAT }
            .sortedByDescending { it.time?.updated ?: it.time?.created ?: Long.MIN_VALUE }
            .take(limit)
        return judged(statuses, recent, recorded, dropChats = true)
    }

    /**
     * The runs of one scheduled mission, newest first, each judged — what a tap on its card opens.
     *
     * Recognised by title ([isRunOf]), because the scheduler remembers only a mission's last run.
     * Capped at [RUNS_SHOWN] **before** the transcripts are fetched: a daily mission accumulates a
     * session a day, and judging each one costs its whole transcript.
     */
    suspend fun missionRuns(name: String): List<Mission> {
        val statuses = api.status()
        val runs = api.sessions()
            .filter { session -> session.title?.let { isRunOf(it, name) } == true }
            .sortedByDescending { it.time?.updated ?: it.time?.created ?: Long.MIN_VALUE }
            .take(RUNS_SHOWN)
        return judged(statuses, runs)
    }

    /**
     * Each session with its state resolved.
     *
     * The status map is fetched **once** by the caller — there is no per-session status route, and
     * asking N times would be N identical answers. Messages, on the other hand, are per session and
     * only fetched for those the status map does not report as active: a running mission needs no
     * verdict.
     */
    private suspend fun judged(
        statuses: Map<String, EngineSessionStatus>,
        sessions: List<EngineSession>,
        recorded: Map<String, EngineSessionKind> = emptyMap(),
        dropChats: Boolean = false,
    ): List<Mission> {
        val learned = mutableMapOf<String, EngineSessionKind>()
        val missions = sessions.mapNotNull { session ->
            val active = statuses[session.id]
            val messages = if (active != null && active.type != IDLE_STATUS) {
                emptyList()
            } else {
                runCatching { api.messages(session.id) }.getOrDefault(emptyList())
            }
            if (dropChats && recorded[session.id] == null) {
                // The transcript is already here: classifying costs nothing more, and remembering
                // the verdict spares the drawer from reading it again.
                val kind = classifySession(session.title, null, messages)
                if (kind != null) learned[session.id] = kind
                if (kind == EngineSessionKind.CHAT) return@mapNotNull null
            }
            Mission(
                sessionId = session.id,
                title = session.title.orEmpty().ifBlank { session.id },
                state = judgeMission(active, messages),
                lastActivityMillis = lastActivityOf(session, messages),
            )
        }
        remember(learned)
        return missions
    }

    /**
     * The chats of the drawer (D-077), most recently active first.
     *
     * Classified by [classifySession]: what this device recorded first, then the scheduler's title
     * shape, and only then the transcript — read for at most [MAX_CLASSIFICATION_READS] unknown
     * sessions per refresh, and the verdict recorded so it is never read again. A session that says
     * nothing yet (no message) is left out: it is either a chat being created right now, which its
     * creator recorded, or nobody's conversation.
     */
    suspend fun recentChats(limit: Int = RECENT_CHATS_SHOWN): List<EngineChatSummary> {
        val statuses = runCatching { api.status() }.getOrDefault(emptyMap())
        val recorded = recordedKinds()
        val sessions = api.sessions()
            .sortedByDescending { it.time?.updated ?: it.time?.created ?: Long.MIN_VALUE }
        val learned = mutableMapOf<String, EngineSessionKind>()
        var reads = 0
        val chats = mutableListOf<EngineChatSummary>()
        for (session in sessions) {
            if (chats.size >= limit) break
            var kind = classifySession(session.title, recorded[session.id], messages = null)
            if (kind == null && reads < MAX_CLASSIFICATION_READS) {
                reads++
                val messages = runCatching { api.messages(session.id) }.getOrNull()
                kind = classifySession(session.title, null, messages)
                if (kind != null) learned[session.id] = kind
            }
            if (kind == EngineSessionKind.CHAT) {
                val active = statuses[session.id]
                chats += EngineChatSummary(
                    sessionId = session.id,
                    title = session.title.orEmpty().ifBlank { session.id },
                    lastActivityMillis = session.time?.updated ?: session.time?.created,
                    running = active != null && active.type != IDLE_STATUS,
                )
            }
        }
        remember(learned)
        return chats
    }

    /**
     * Starts a chat (D-077): an engine session on the `chat` profile, then its first message.
     *
     * The mission path, with the chat's three differences and nothing else:
     *
     *  * the agent is `chat` — no shell, no local files, no web fetch;
     *  * the perimeter is **every connector the scheduler opens to a chat** ([chatPerimeter]):
     *    recorded with the scheduler before the first prompt, exactly as a mission's is, and turned
     *    into the session's rules by the same [permissionsFor];
     *  * the model comes from `hobbitton-chat` ([forChat]), which carries the chat budget — the
     *    chat provider's default when none is named.
     *
     * The kind is recorded as soon as the session exists, before anything can fail: a chat whose
     * first prompt was lost is still a chat, and the drawer shows it as one.
     */
    suspend fun startChat(
        text: String,
        model: EngineModelRef? = null,
        files: List<EnginePromptPart> = emptyList(),
    ): String {
        val catalogue = connectors()
        val perimeter = catalogue.chatPerimeter()
        val session = api.createSession(
            CreateEngineSessionRequest(
                agent = CHAT_AGENT,
                title = text.trim().ifBlank { files.firstOrNull()?.filename.orEmpty() }.take(TITLE_LENGTH),
                permission = permissionsFor(catalogue, perimeter),
            ),
        )
        runCatching { kinds.record(session.id, EngineSessionKind.CHAT) }
            .onFailure { Logger.w(it) { "Could not record a new chat's kind" } }
        scheduler.setScope(session.id, perimeter)
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
     * The model a chat's turn runs on: the one asked for, moved onto the chat's provider, or the
     * chat provider's own default. Named whenever the catalogue answers: an absent model would
     * leave the choice to the engine, and the chat budget is not something to leave to it.
     */
    private suspend fun chatModel(requested: EngineModelRef?): EngineModelRef? =
        requested?.forChat() ?: runCatching { models(EngineProfile.CHAT).preselected?.ref }.getOrNull()

    private suspend fun recordedKinds(): Map<String, EngineSessionKind> =
        runCatching { kinds.all() }.getOrDefault(emptyMap())

    private suspend fun remember(learned: Map<String, EngineSessionKind>) {
        if (learned.isEmpty()) return
        runCatching { kinds.recordAll(learned) }
            .onFailure { Logger.w(it) { "Could not record the kinds of ${learned.size} session(s)" } }
    }

    /**
     * Starts a mission: a session with its permissions, then the objective.
     *
     * The two calls cannot be merged — the engine has no « create and run » route — so a failure
     * between them leaves a session that exists and has done nothing. That is why the session id is
     * returned even on that path: an orphan the tab can show and abort beats an orphan nobody knows
     * about. Same reasoning as the scheduler's, which records the session id before the mission
     * produces anything (brief §6, phase 3).
     *
     * [model] travels with the **prompt**, and never with the session. That is not a style
     * preference: since OpenCode 1.18.18 `POST /session` rejects a `model` key outright — HTTP 400
     * `{"_tag":"BadRequest"}`, naming no field — so putting it there kills the mission before it
     * exists. The server learned this the hard way on 24/08/2026, when the only scheduled mission
     * that named a model failed in 0,0 s for three nights running; its watchdog carries the same
     * comment (`scheduler/moteur.py`). The prompt is where the model decides the call anyway.
     *
     * Null means « whatever the profile is configured with » — an absent key, not an empty one, so
     * the engine's own default applies untouched.
     *
     * **Interactive, always** (26/09/2026): a mission launched from the app is one somebody is
     * looking at, so nothing the catalogue reserves for a watched session (`shell`, the annuaire)
     * is withheld. The scheduler's missions are the autonomous ones, and they never come through
     * here.
     */
    suspend fun launch(
        objective: String,
        connectors: List<String>,
        model: EngineModelRef? = null,
    ): String {
        val session = api.createSession(
            CreateEngineSessionRequest(
                agent = MISSION_AGENT,
                title = objective.take(TITLE_LENGTH),
                permission = permissionsFor(connectors(), connectors),
            ),
        )
        // The scope goes to the scheduler before the first prompt (D-071): the annuaire consults
        // it from the mission's very first tool call. A recording that fails is a launch that
        // fails — going on regardless would hand the mission the whole catalogue, the opposite of
        // what an unticked box promised. The engine keeps an empty, never-prompted session; that
        // is harmless, and cheaper than an abort call whose behaviour on an idle session is
        // unmeasured.
        // The agent is known: it is the one just written on the session. Every later turn names it
        // without reading the transcript back (see [sendMessage]).
        rememberAgent(session.id, MISSION_AGENT)
        scheduler.setScope(session.id, connectors)
        api.prompt(
            sessionId = session.id,
            request = EnginePromptRequest(
                parts = listOf(EnginePromptPart(text = objective)),
                agent = MISSION_AGENT,
                model = model,
                system = instructions(),
            ),
        )
        return session.id
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
        val messages = api.messages(sessionId)
        // The transcript is here anyway: the session's agent is read off it for free, so the
        // next task turn does not fetch it again (see [taskAgentOf]).
        agentWrittenOn(messages)?.let { rememberAgent(sessionId, taskAgent(it), onlyIfUnknown = true) }
        return engineHistoryEvents(messages)
    }

    /**
     * What is happening in the session right now. The engine's feed is global; the client keeps only
     * this session's frames.
     */
    fun events(sessionId: String): Flow<EngineStreamEvent> = streamClient.connect(sessionId, eventTransport)

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
        profile: EngineProfile = EngineProfile.TASK,
    ): List<EngineStreamEvent> {
        // EVERY turn names its agent. A turn that names none does NOT stay on the session's agent:
        // the engine runs it on its default agent, `build`, which has none of the session's rules —
        // a task's follow-up ran there on 29/09/2026. A chat names `chat` and its provider (D-077),
        // or it would also leave the chat's budget; a task names the session's own agent
        // ([taskAgentOf]) and keeps the session's model unless one is picked.
        val chat = profile == EngineProfile.CHAT
        return engineHistoryEvents(
            listOf(
                api.sendMessage(
                    sessionId,
                    text,
                    model = if (chat) chatModel(model) else model,
                    files = files,
                    system = instructions(),
                    agent = if (chat) CHAT_AGENT else taskAgentOf(sessionId),
                ),
            ),
        )
    }

    /**
     * The agent a task session runs on, for its next turn: remembered for the process, read once
     * off the transcript otherwise ([agentWrittenOn], the same reading [classifySession] uses),
     * and [MISSION_AGENT] when nothing can be read. Never null, never `build` nor `plan`
     * ([taskAgent]).
     *
     * A transcript that cannot be fetched is not remembered: the fallback holds for this turn,
     * and the next one tries again.
     */
    private suspend fun taskAgentOf(sessionId: String): String {
        agentsMutex.withLock { sessionAgents[sessionId] }?.let { return it }
        val messages = runCatching { api.messages(sessionId) }
            .onFailure { Logger.w(it) { "Could not read a task's agent; the turn names $MISSION_AGENT" } }
            .getOrNull()
            ?: return MISSION_AGENT
        val agent = taskAgent(agentWrittenOn(messages))
        rememberAgent(sessionId, agent)
        return agent
    }

    private suspend fun rememberAgent(sessionId: String, agent: String, onlyIfUnknown: Boolean = false) {
        agentsMutex.withLock {
            if (!onlyIfUnknown || sessionId !in sessionAgents) sessionAgents[sessionId] = agent
        }
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
     * Re-grants a **live** session's connectors, replacing its whole ruleset.
     *
     * Unticking therefore revokes. That the engine takes this at all is what lets the conversation
     * offer connector chips: a mission launched with memory alone can be handed the mail connector
     * without a restart, and without losing the transcript that is its only record.
     */
    suspend fun setConnectors(sessionId: String, connectors: List<String>) {
        // The scope first (D-071): if recording it fails, nothing has changed and the caller hears
        // it; recording it after the rules would leave a session whose direct tools moved while
        // the annuaire still serves yesterday's list.
        scheduler.setScope(sessionId, connectors)
        // Never autonomous here: someone is looking at the screen, which is the whole premise of
        // §4.2's ban — an approval prompt nobody answers is not a safeguard, but one they *do*
        // answer is exactly the supervision the rule asks for.
        api.setPermissions(sessionId, permissionsFor(connectors(), connectors))
    }

    private var cachedConnectors: ConnectorCatalogue? = null

    /**
     * Each task session's agent, as [taskAgentOf] resolved it: for the process only. A session's
     * agent never changes, so there is nothing to invalidate; a restart reads it once more.
     */
    private val sessionAgents = mutableMapOf<String, String>()
    private val agentsMutex = Mutex()

    private companion object {
        const val TITLE_LENGTH = 60

        /** The engine's word for a session that is doing nothing; any other status is work. */
        const val IDLE_STATUS = "idle"

        /** A month of a daily mission: enough to see a pattern, few enough transcripts to fetch. */
        const val RUNS_SHOWN = 30

        /** Two days of the scheduler's output: what one scrolls, not what one searches. */
        const val RECENT_SHOWN = 20

        /** The drawer's conversations: a few weeks of chatting, not an archive. */
        const val RECENT_CHATS_SHOWN = 40

        /**
         * Transcripts read per refresh to classify sessions this device has never seen. Each
         * verdict is recorded, so a backlog drains over a few openings instead of one slow one.
         */
        const val MAX_CLASSIFICATION_READS = 10

        // The agent a mission runs on is `MISSION_AGENT` (EngineProfile.kt), next to the chat's.
        // There is no profile choice here and no profile *notion* either: what a mission may do is
        // its ticked connectors, what it must do is its objective, and how it should behave is the
        // global profile. The server's other profiles are what the SCHEDULED missions run on.

        /**
         * `source` of a provider this deployment declared in its own `opencode.json`, as opposed
         * to `custom` for one OpenCode ships with. Measured against the live engine on 28/08/2026:
         * `hobbitton-gateway` reports `config`, OpenCode's own bundled endpoint reports `custom`.
         */
        const val DECLARED_PROVIDER = "config"
    }
}

/**
 * When a session last moved: the latest of its messages, or the session's own `updated` when there
 * are none to read.
 *
 * The messages are the exact answer and they cost nothing extra —
 * [EngineMissionRepository.recentMissions] already fetches them to judge a finished mission. A
 * **running** one is the case with no messages: they are deliberately not fetched (a mission in
 * flight needs no verdict, and pulling its whole transcript on every refresh would download the
 * history of the tab). `time.updated` stands in there; it is the engine's own field and the only
 * one available without a second round trip.
 *
 * A message's `completed` is preferred over its `created`: an assistant turn is created when it
 * starts and completed when it stops, and a ten-minute turn that began at 03:00 last spoke at 03:10.
 * Falling back to `created` and finally to the session's own dates means the list still sorts when
 * the engine omits a field, rather than dropping a row to the bottom.
 */
internal fun lastActivityOf(session: EngineSession, messages: List<EngineMessage>): Long? {
    val lastMessage = messages.mapNotNull { it.info.time?.let { time -> time.completed ?: time.created } }
        .maxOrNull()
    return lastMessage ?: session.time?.updated ?: session.time?.created
}
