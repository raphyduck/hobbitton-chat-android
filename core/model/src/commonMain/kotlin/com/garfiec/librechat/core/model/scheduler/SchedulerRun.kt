package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One run of a scheduled mission, as the scheduler's `historique(json_brut=True)` lists it
 * (16/10/2026).
 *
 * [hasNews] is the run's own verdict: false when it finished saying it found nothing
 * (« RIEN À SIGNALER »), true otherwise (a failure, a run stopped short, a report), null for a run
 * recorded before the scheduler kept it. It decides whether the run's conversation counts as
 * unread: a mission run every half hour that finds nothing must not crowd the inbox.
 *
 * [discreet] (19/10/2026): its mission keeps its runs out of the conversation lists (a background
 * mission, which speaks through a report or a decision conversation); only a failed run shows.
 */
@Serializable
data class SchedulerRun(
    val session: String? = null,
    val mission: String? = null,
    @SerialName("declenchee") val trigger: String? = null,
    @SerialName("succes") val succeeded: Boolean? = null,
    @SerialName("incomplet") val incomplete: Boolean = false,
    @SerialName("a_signaler") val hasNews: Boolean? = null,
    @SerialName("discrete") val discreet: Boolean = false,
)
