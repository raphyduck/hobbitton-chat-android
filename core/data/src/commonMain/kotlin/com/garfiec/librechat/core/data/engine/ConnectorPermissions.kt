package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.isAuto
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue

/*
 * The session's rules are the scheduler's (10/11/2026, Raphaël: « c'est le serveur qui définit
 * les modes »): the app tells `perimetre` the conversation's mode and the direct connectors ticked
 * ([directPerimeter]), and the scheduler writes the rules of the role that leads in that mode. The
 * app wrote them itself from 26/09 to 10/11/2026 (`permissionsFor`, with a table of the roles'
 * access copied from the server); the conversations opened then still carry those rules, which
 * are only READ here, for the chip, until the first send has the scheduler rewrite them.
 */

/**
 * The latest ruleset of an accumulated list: what follows its opening marker, a `task` → deny on
 * every pattern (09/11/2026), or the `*` → deny the rulesets before that day opened with.
 */
private fun standingBlock(rules: List<EnginePermissionRule>): List<EnginePermissionRule> =
    rules.drop(maxOf(lastWildcardDeny(rules), lastTaskDeny(rules)) + 1)

private fun lastWildcardDeny(rules: List<EnginePermissionRule>): Int =
    rules.indexOfLast { it.permission == ANY_TOOL && it.action == ACTION_DENY }

private fun lastTaskDeny(rules: List<EnginePermissionRule>): Int =
    rules.indexOfLast { it.permission == TASK_TOOL && it.pattern == ANY_TOOL && it.action == ACTION_DENY }

/**
 * Which connectors a ruleset this app wrote before 10/11/2026 actually grants.
 *
 * **The ruleset accumulates.** `PATCH /session/{id}` appends its rules; it does not replace them.
 * Measured 31/08/2026 on a live mission: **1 016 rules in 21 blocks**, each block a whole ruleset
 * opening with its own `*` → `deny`, growing 14 → 3 → 13 → 23 → … → 112 tools as connectors were
 * ticked one after another. A rule found anywhere in that list is therefore not a fact about the
 * session — reading it that way, as this did when it shipped that morning, reported every connector
 * ever ticked, including any since unticked.
 *
 * So only the **last block** is read ([standingBlock]): the rules after the final opening marker,
 * which is precisely the ruleset the last `PATCH` sent.
 *
 * **This is the cautious reading, and deliberately so, because the engine's own rule is not
 * settled.** Two places in this project describe it and they disagree: the server's `moteur.py`
 * says the last matching rule wins, [EnginePermissionRule] says the most specific match wins. Under
 * the first, the last block is exactly the standing grant. Under the second, a tool allowed only by
 * an earlier block would still be allowed and this reports it as off — understating what the
 * session can reach rather than overstating it, which is the only direction a capability chip may
 * err in. Settling it needs a measurement nobody has made: revoke a tool, then call it.
 *
 * A connector counts as on when the standing block allows **one** of its tools (31/10/2026). It
 * used to take every tool it declares, so that a ruleset this app or the scheduler wrote would
 * round-trip exactly and a hand-written one opening half a connector would read as off. But a
 * ruleset is written once, at the session's creation, and the catalogue moves on: the day the
 * scheduler gained `planificateur_pieces_jointes` (30/10/2026), every conversation opened before
 * read its `planificateur` as absent, and the next rewrite of its rules (the lift to the titulaire's
 * level, a tick) stripped the whole connector, the new tool with it (Raphaël, 31/10/2026: « cette
 * conversation n'en a pas »). The tools a ruleset predates are never named in it, so they cannot
 * count against it.
 *
 * `ask` and `deny` are not grants. A connector that declares no tool is never on.
 */
fun connectorsGranted(
    catalogue: ConnectorCatalogue,
    rules: List<EnginePermissionRule>,
): Set<String> {
    val standing = standingBlock(rules)
        .filter { it.action == ACTION_ALLOW }
        .map { it.permission }
        .toSet()
    return catalogue.connecteurs
        .filterValues { grant -> grant.outils.any { it in standing } }
        .keys
}

/** The one rule action that grants. */
private const val ACTION_ALLOW = "allow"

private const val ACTION_DENY = "deny"

/** The catch-all the rulesets before 09/11/2026 opened with; never written since, still read. */
private const val ANY_TOOL = "*"

/** The engine's delegation tool; its rules name a sub-agent in their pattern. */
private const val TASK_TOOL = "task"

/**
 * The connectors this catalogue offers a mission, in the order the picker should show them.
 *
 * Every one is tickable: a mission launched or talked to from this app is watched (26/09/2026), so
 * nothing the catalogue reserves for a watched session is barred.
 */
fun ConnectorCatalogue.offered(): List<ConnectorOption> =
    connecteurs.entries.sortedBy { it.key }.map { (name, grant) ->
        ConnectorOption(
            name = name,
            toolCount = grant.outils.size,
            // A direct connector is ticked when the scheduler says so, on cost. One the annuaire
            // serves costs nothing until it is called, and the annuaire's promise is reach — so
            // it is ticked, and unticking it is what narrows the scope (D-071).
            tickedByDefault = grant.tickedByDefault || !grant.direct,
            viaAnnuaire = !grant.direct,
            // The scheduler's `agit`; what it refuses to unwatched missions, from one that predates it.
            actsForYou = grant.acts || grant.refusedWhenAutonomous,
        )
    }

/**
 * The trust level every session this app builds stands at (D-083, 22/10/2026): the person signed
 * in through the portal. The scheduler, not the app, decides what that level reaches.
 */
const val TITULAR_LEVEL = "titulaire"

/**
 * The mode of a conversation on « Piloté » (10/11/2026, Raphaël: the server defines the modes):
 * the scheduler writes the rules of the role that leads in it, and the engine runs it on the `chat`
 * agent's own model, the pilot in service. No model goes with its messages.
 */
const val PILOTED_MODE = "pilote"

/**
 * The mode the scheduler is told a conversation runs in: [PILOTED_MODE] without a model (or on
 * « Piloté »'s reference), else the model as `provider/model`, whose role the scheduler knows.
 */
fun conversationMode(model: EngineModelRef?): String =
    model?.takeUnless { it.isAuto }?.let { "${it.providerId}/${it.modelId}" } ?: PILOTED_MODE

/** The one connector the titulaire's level leaves on demand: the shell inherits everything. */
const val SHELL_CONNECTOR = "shell"

/**
 * The direct connectors a session's rules open: of [ticks], those the catalogue declares to the
 * engine itself; without ticks, every direct connector but the shell (the scheduler's own default
 * for a conversation, `CONNECTEURS_PAR_DEFAUT`).
 *
 * Raphaël's rule (24/10/2026): the app chooses only among the engine's **direct** connectors
 * (memory, workspace, web, shell, annuaire, scheduler, alerts); whatever the annuaire serves is
 * the scheduler's decision, from WHO speaks to the session (D-083), and never goes into the
 * rules (D-071). So the sheet lists [directOffered] and the ticks reach the engine through here.
 */
fun ConnectorCatalogue.directPerimeter(ticks: Collection<String>?): List<String> {
    val direct = connecteurs.filterValues { it.direct }.keys
    val chosen = ticks?.toSet() ?: (direct - SHELL_CONNECTOR)
    return direct.filter { it in chosen }.sorted()
}

/** The connectors the sheet offers: the engine's direct ones only, the annuaire's being the scheduler's. */
fun ConnectorCatalogue.directOffered(): List<ConnectorOption> = offered().filter { !it.viaAnnuaire }

/** One tickable connector, as a picker needs it. */
data class ConnectorOption(
    val name: String,
    val toolCount: Int,
    /** Ticked when the sheet opens. The scheduler decides which, on cost — see [ConnectorGrant]. */
    val tickedByDefault: Boolean = false,
    /** Reached through the annuaire rather than declared to the model — see [ConnectorGrant.direct]. */
    val viaAnnuaire: Boolean = false,
    /**
     * Sends, writes, deletes or otherwise acts in the person's name ([ConnectorGrant.acts], the
     * scheduler's `agit`, or failing it [ConnectorGrant.refusedWhenAutonomous]). The sheet lists
     * these apart, under « Agir en votre nom » (D-082).
     */
    val actsForYou: Boolean = false,
)
