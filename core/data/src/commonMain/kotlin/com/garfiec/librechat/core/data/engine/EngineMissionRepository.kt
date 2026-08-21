package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.engine.judgeMission
import com.garfiec.librechat.core.network.api.AgentEngineApi

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
 * The missions of the Tasks tab, read from the engine and judged here.
 *
 * **Nothing is cached locally.** The engine is the source of truth: a mission that exists only on a
 * phone is a mission nobody can supervise — not from the web, not from the scheduler, not from
 * another device. That is also why the list is rebuilt on each refresh rather than merged into a
 * local store.
 */
class EngineMissionRepository(
    private val api: AgentEngineApi,
) {

    suspend fun profiles(): List<EngineAgentProfile> = api.profiles()

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
     */
    suspend fun launch(
        profile: String,
        objective: String,
        connectors: List<String>,
        title: String? = null,
        autonomous: Boolean = true,
    ): String {
        val session = api.createSession(
            CreateEngineSessionRequest(
                agent = profile,
                title = title ?: objective.take(TITLE_LENGTH),
                permission = permissionsFor(connectors, autonomous),
            ),
        )
        api.prompt(
            sessionId = session.id,
            request = EnginePromptRequest(
                parts = listOf(EnginePromptPart(text = objective)),
                agent = profile,
            ),
        )
        return session.id
    }

    suspend fun abort(sessionId: String) = api.abort(sessionId)

    private companion object {
        const val TITLE_LENGTH = 60
    }
}

/**
 * What the mission is allowed to touch.
 *
 * Two rules, both from the brief (§4) and both easy to get backwards:
 *
 *  * the list **opens with a `*` deny**. A profile is a ceiling of capabilities, and the checkboxes
 *    narrow it for this mission only — never widen it. Starting from « allow everything » and
 *    subtracting would make a forgotten connector a granted one.
 *  * `shell` is refused outright to an autonomous mission. Nobody is watching one, and an approval
 *    prompt nobody answers is not a safeguard — the mission would simply hang until the watchdog
 *    kills it, which is the good case.
 */
internal fun permissionsFor(connectors: List<String>, autonomous: Boolean): List<EnginePermissionRule> {
    val allowed = connectors.filterNot { autonomous && it in FORBIDDEN_WHEN_AUTONOMOUS }
    val rules = mutableListOf(EnginePermissionRule(permission = "*", action = "deny"))
    allowed.flatMap { CONNECTOR_PATTERNS[it].orEmpty() }
        .distinct()
        .forEach { pattern -> rules += EnginePermissionRule(permission = pattern, action = "allow") }
    return rules
}

/** Mirrors the server's `scheduler/moteur.py`; the two must not drift. */
private val CONNECTOR_PATTERNS = mapOf(
    "memoire" to listOf("memoire_lire", "memoire_rechercher", "memoire_lister", "memoire_retroliens"),
    "memoire-ecriture" to listOf("memoire_ecrire", "memoire_journaliser", "memoire_reindexer"),
    "fichiers" to listOf("read", "write", "edit", "glob", "grep", "list"),
    "shell" to listOf("bash"),
)

private val FORBIDDEN_WHEN_AUTONOMOUS = setOf("shell")
