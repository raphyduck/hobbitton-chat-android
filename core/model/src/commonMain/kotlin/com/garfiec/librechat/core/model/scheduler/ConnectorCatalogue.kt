package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The connectors a mission may be given, as the scheduler declares them.
 *
 * **Fetched, never copied.** This app used to carry its own hand-written table of four connectors —
 * out of the nineteen the platform has — and for `fichiers` it named tools that do not exist
 * (`read`, `write`, `edit`… instead of `fichiers_list_roots`, `fichiers_read_text`…). Nothing
 * failed: a permission rule allowing a tool nobody offers is accepted without a word. So the
 * mission launched with an empty toolbox and found out on its own, mid-run — *« cette mission n'a
 * pas d'outil email »*. Reported from the phone on 30/08/2026.
 *
 * That is the failure mode `scripts/mirrors.json` exists to catch, and the reason this one is not
 * in that registry: the fix is not another drift check, it is having no copy. The scheduler's
 * `connecteurs` tool serves this, and `moteur.py` builds it from the same `CONNECTEURS` its own
 * missions run on — so the two cannot disagree.
 */
@Serializable
data class ConnectorCatalogue(
    val connecteurs: Map<String, ConnectorGrant> = emptyMap(),
    /**
     * What a session is granted **on top of** the ticked connectors. Ignoring it builds
     * incomplete rules — and, like the rest of this, silently.
     */
    val socle: Map<String, String> = emptyMap(),
)

@Serializable
data class ConnectorGrant(
    val outils: List<String> = emptyList(),
    /**
     * Refused to an autonomous mission (brief §4.2): nobody is watching one, so an approval prompt
     * is not a safeguard. The picker greys it out rather than letting the server refuse the launch.
     */
    @SerialName("refuse_si_autonome")
    val refusedWhenAutonomous: Boolean = false,
    /**
     * Ticked when the new-mission sheet opens.
     *
     * The scheduler decides which ones, and it decides on cost: every ticked connector reloads its
     * catalogue to the model on **every turn** (~700 tokens per tool), so ticking all thirty would
     * spend a mission's budget before it did anything. The server's socle is reading only — memory,
     * files, web search, bank accounts, the schedule's state — and never anything that acts.
     *
     * Defaults to false, so a scheduler that does not serve the field yet ticks nothing rather than
     * everything.
     */
    @SerialName("defaut")
    val tickedByDefault: Boolean = false,
)
