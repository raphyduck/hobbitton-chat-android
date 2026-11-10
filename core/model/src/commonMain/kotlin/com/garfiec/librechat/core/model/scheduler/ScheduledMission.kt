package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A recurring mission as the scheduler knows it.
 *
 * Field names are the scheduler's, in French, and deliberately not translated on the wire: the
 * server's `vue.py` is the contract, and a rename on either side that the other does not follow
 * would produce an empty list on a phone without anything failing anywhere. Its tests assert the
 * names for the same reason.
 */
@Serializable
data class ScheduledMission(
    @SerialName("nom") val name: String,
    @SerialName("profil") val profile: String,
    @SerialName("active") val enabled: Boolean,
    /** Five-field crontab, or null for a one-shot — [runAt] carries the date then. */
    @SerialName("cron") val cron: String? = null,
    @SerialName("quand") val runAt: String? = null,
    @SerialName("fuseau") val timeZone: String = "",
    /** Next due time, ISO 8601. Null when a one-shot has already fired, or a cron is broken. */
    @SerialName("prochaine") val nextRun: String? = null,
    @SerialName("connecteurs") val connectors: List<String> = emptyList(),
    /**
     * How many tools this mission declares to the model on **every** turn. It is this number,
     * multiplied by the turns, that decides whether a mission fits its budget — so it is shown
     * rather than left to be inferred from a bill.
     */
    @SerialName("outils_declares") val declaredTools: Int = 0,
    @SerialName("modele") val model: String? = null,
    @SerialName("timeout_s") val timeoutSeconds: Int = 0,
    @SerialName("budget_tokens") val tokenBudget: Int = 0,
    @SerialName("plafond_appels") val toolCallCeiling: Int? = null,
    @SerialName("notifier") val notifies: Boolean = false,
    /** Its runs stay out of the conversation lists, a failure aside (19/10/2026). */
    @SerialName("discrete") val discreet: Boolean = false,
    @SerialName("en_cours") val running: Boolean = false,
    @SerialName("derniere") val lastRun: MissionRun? = null,
    /**
     * What `etat` leaves out and the `mission` tool adds (04/11/2026, the mission's own screen):
     * the prompt, and the explicit tool list when the mission has one (null: it follows its
     * connectors).
     */
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("outils") val tools: List<String>? = null,
)

/**
 * What the mission's screen may change (04/11/2026). Null leaves a field alone: server-side
 * `modifier` merges, and only what the person touched travels, so an unchanged prompt is never
 * resent (and never truncated by a client that did not have it whole).
 */
data class MissionEdits(
    val cron: String? = null,
    val runAt: String? = null,
    val prompt: String? = null,
    val model: String? = null,
    val tokenBudget: Int? = null,
    val timeoutSeconds: Int? = null,
    val toolCallCeiling: Int? = null,
    val notifies: Boolean? = null,
    val discreet: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = listOf(cron, runAt, prompt, model, tokenBudget, timeoutSeconds, toolCallCeiling, notifies, discreet)
            .all { it == null }
}

/**
 * The last time this mission ran.
 *
 * [succeeded] is nullable on purpose: it is null *while the mission is running*, and rendering that
 * as `false` would show a red failure on a mission that is working.
 */
@Serializable
data class MissionRun(
    @SerialName("debut") val startedAt: String? = null,
    @SerialName("fin") val endedAt: String? = null,
    @SerialName("duree_s") val durationSeconds: Double? = null,
    @SerialName("jetons") val tokens: Int? = null,
    /** Why it stopped, in the scheduler's words: « terminée », « BUDGET DÉPASSÉ (…) », … */
    @SerialName("arret") val stopReason: String? = null,
    @SerialName("succes") val succeeded: Boolean? = null,
    /**
     * Stopped cleanly but short of the end — out of time or out of calls, with a report handed in.
     *
     * The scheduler's own verdict, and the only place it exists: the engine sees a session that
     * spoke and consumed tokens, which it cannot tell apart from one that finished. A field of its
     * own rather than a third value of `succes` — the server kept that one a boolean so older
     * clients still decode (`vue.py`) — so `succes` alone never says whether the job got done.
     */
    @SerialName("incomplet") val incomplete: Boolean = false,
    @SerialName("session") val sessionId: String? = null,
)

@Serializable
data class SchedulerState(
    @SerialName("missions") val missions: List<ScheduledMission> = emptyList(),
    /** The sessions the scheduler holds in pause on a spending ceiling (07/11/2026). */
    @SerialName("pauses") val pauses: List<PausedSession> = emptyList(),
)

/**
 * A session the scheduler paused because the gateway refused its last step on a spending ceiling
 * (07/11/2026, Raphaël's rule: a conversation out of budget waits for it to come back and resumes
 * by itself). The scheduler's `pauses_plafond` tool and `etat`'s `pauses` list one row per session.
 */
@Serializable
data class PausedSession(
    val session: String,
    /** When the pause began, ISO 8601. */
    @SerialName("depuis") val since: String? = null,
    /** The gateway's own refusal, summarised. */
    @SerialName("motif") val reason: String? = null,
    /** The mission whose run this is, or null for a conversation. */
    val mission: String? = null,
    /** `pause` while waiting, `reprise` once the resume order left and is being confirmed. */
    @SerialName("etat") val state: String? = null,
    /** How many resume attempts the gateway refused since. */
    @SerialName("reprises") val attempts: Int = 0,
)
