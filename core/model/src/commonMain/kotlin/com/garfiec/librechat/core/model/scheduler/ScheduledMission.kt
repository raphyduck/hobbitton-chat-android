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
    @SerialName("en_cours") val running: Boolean = false,
    @SerialName("derniere") val lastRun: MissionRun? = null,
)

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
    @SerialName("session") val sessionId: String? = null,
)

@Serializable
data class SchedulerState(
    @SerialName("missions") val missions: List<ScheduledMission> = emptyList(),
)
