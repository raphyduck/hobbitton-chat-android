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
 *
 * [inConversation] (26/10/2026): the run was played inside a conversation of the app, a reminder
 * set from it (19/10/2026) or a relance posing its decision in its own conversation (kit #147),
 * not in a session of its own. Such a run says nothing about the conversation's kind, title or
 * unread state: the conversation keeps its own, and is unread whenever it moved.
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
    @SerialName("conversation") val inConversation: Boolean = false,
)
