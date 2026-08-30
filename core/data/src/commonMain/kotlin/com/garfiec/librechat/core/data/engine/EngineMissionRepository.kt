package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineProviderModel
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
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
    val createdAtMillis: Long?,
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
) {

    suspend fun profiles(): List<EngineAgentProfile> = api.profiles()

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
    suspend fun models(): EngineModelChoice {
        val catalogue = api.providers()
        val declared = catalogue.providers.filter { it.source == DECLARED_PROVIDER }

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
     * The list, with each mission's state resolved.
     *
     * The status map is fetched **once** for the whole list — there is no per-session status route,
     * and asking N times would be N identical answers. Messages, on the other hand, are per session
     * and only fetched for those the status map does not report as active: a running mission needs
     * no verdict, and pulling every message of every past mission to render a list would download
     * the entire history on each refresh.
     */
    suspend fun missions(): List<Mission> {
        val statuses = api.status()
        return api.sessions().map { session ->
            val active = statuses[session.id]
            val messages = if (active != null && active.type != "idle") {
                emptyList()
            } else {
                runCatching { api.messages(session.id) }.getOrDefault(emptyList())
            }
            Mission(
                sessionId = session.id,
                title = session.title.orEmpty().ifBlank { session.id },
                state = judgeMission(active, messages),
                createdAtMillis = session.time?.created,
            )
        }
    }

    suspend fun messages(sessionId: String): List<EngineMessage> = api.messages(sessionId)

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
     */
    suspend fun launch(
        profile: String,
        objective: String,
        connectors: List<String>,
        title: String? = null,
        autonomous: Boolean = true,
        model: EngineModelRef? = null,
    ): String {
        val session = api.createSession(
            CreateEngineSessionRequest(
                agent = profile,
                title = title ?: objective.take(TITLE_LENGTH),
                permission = permissionsFor(connectors(), connectors, autonomous),
            ),
        )
        api.prompt(
            sessionId = session.id,
            request = EnginePromptRequest(
                parts = listOf(EnginePromptPart(text = objective)),
                agent = profile,
                model = model,
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
    suspend fun history(sessionId: String): List<EngineStreamEvent> =
        engineHistoryEvents(api.messages(sessionId))

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
    ): List<EngineStreamEvent> =
        engineHistoryEvents(listOf(api.sendMessage(sessionId, text, model = model)))

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
     * Re-grants a **live** session's connectors, replacing its whole ruleset.
     *
     * Unticking therefore revokes. That the engine takes this at all is what lets the conversation
     * offer connector chips: a mission launched with memory alone can be handed the mail connector
     * without a restart, and without losing the transcript that is its only record.
     */
    suspend fun setConnectors(sessionId: String, connectors: List<String>) {
        // Never autonomous here: someone is looking at the screen, which is the whole premise of
        // §4.2's ban — an approval prompt nobody answers is not a safeguard, but one they *do*
        // answer is exactly the supervision the rule asks for.
        api.setPermissions(sessionId, permissionsFor(connectors(), connectors, autonomous = false))
    }

    private var cachedConnectors: ConnectorCatalogue? = null

    private companion object {
        const val TITLE_LENGTH = 60

        /**
         * `source` of a provider this deployment declared in its own `opencode.json`, as opposed
         * to `custom` for one OpenCode ships with. Measured against the live engine on 28/08/2026:
         * `hobbitton-gateway` reports `config`, OpenCode's own bundled endpoint reports `custom`.
         */
        const val DECLARED_PROVIDER = "config"
    }
}

/**
 * What the mission is allowed to touch, built from the scheduler's own catalogue.
 *
 * Three rules, and the first is why this function takes a [catalogue] instead of holding a table:
 *
 *  * **nothing is copied.** This module used to carry its own map of four connectors — out of the
 *    platform's nineteen — and for `fichiers` it named tools that do not exist (`read`, `write`,
 *    `edit`, `glob`, `grep`, `list`, where the engine offers `fichiers_list_roots`,
 *    `fichiers_read_text`…). A rule allowing a tool nobody serves is accepted in silence, so the
 *    mission simply launched with an empty toolbox and said so mid-run. Reported 30/08/2026. The
 *    catalogue comes from `moteur.py`'s `CONNECTEURS`, the same table the scheduler's own missions
 *    run on, so the two cannot drift.
 *  * the list **opens with a `*` deny**, then re-opens by name. A profile is a ceiling of
 *    capabilities and the checkboxes narrow it for this mission only — never widen it. Starting
 *    from « allow everything » and subtracting would make a forgotten connector a granted one.
 *    Order matters: the engine keeps the **last** rule that matches.
 *  * the catalogue's `socle` is granted **on top**. It is what a session gets besides its
 *    connectors (`todowrite`); dropping it builds rules that are incomplete, and silently so.
 *
 * `shell` is refused outright to an autonomous mission — but that verdict comes from the catalogue
 * (`refusedWhenAutonomous`), not from a constant here. Nobody watches an autonomous mission, and an
 * approval prompt nobody answers is not a safeguard.
 */
fun permissionsFor(
    catalogue: ConnectorCatalogue,
    connectors: List<String>,
    autonomous: Boolean,
): List<EnginePermissionRule> {
    val granted = connectors
        .mapNotNull { name -> catalogue.connecteurs[name]?.let { name to it } }
        .filterNot { (_, grant) -> autonomous && grant.refusedWhenAutonomous }

    val rules = mutableListOf(EnginePermissionRule(permission = "*", action = "deny"))
    catalogue.socle.forEach { (tool, action) ->
        rules += EnginePermissionRule(permission = tool, action = action)
    }
    granted.flatMap { (_, grant) -> grant.outils }
        .distinct()
        .forEach { tool -> rules += EnginePermissionRule(permission = tool, action = "allow") }
    return rules
}

/** The connectors this catalogue offers a mission, in the order the picker should show them. */
fun ConnectorCatalogue.offered(autonomous: Boolean): List<ConnectorOption> =
    connecteurs.entries.sortedBy { it.key }.map { (name, grant) ->
        ConnectorOption(
            name = name,
            toolCount = grant.outils.size,
            // Disabled rather than hidden: someone who wonders where shell went gets an answer,
            // instead of a missing row to puzzle over.
            enabled = !(autonomous && grant.refusedWhenAutonomous),
        )
    }

/** One tickable connector, as a picker needs it. */
data class ConnectorOption(
    val name: String,
    val toolCount: Int,
    val enabled: Boolean,
)
