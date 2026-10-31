package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue

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
 * **Interactive, always** (26/09/2026): every mission this app builds rules for is one somebody is
 * watching, so the catalogue's `refusedWhenAutonomous` is not read here. The autonomous missions are
 * the scheduler's, and their rules are built server-side, not by this function.
 *
 * One guard the catalogue does not get to override (review C11, 26/09/2026): a pattern is taken
 * only when it names one tool. `*`, an empty string or anything carrying a wildcard, from a
 * connector's `outils` or from the `socle`, would re-open everything the opening `*` → `deny` just
 * closed while the screen still showed only the ticked connectors. Such a pattern is logged and
 * skipped; it is a fault in the scheduler's table, not a grant.
 */
fun permissionsFor(
    catalogue: ConnectorCatalogue,
    connectors: List<String>,
): List<EnginePermissionRule> {
    val granted = connectors
        .mapNotNull { name -> catalogue.connecteurs[name]?.let { name to it } }
        // Only the direct connectors become rules (D-071). The rest is the annuaire's, which reads
        // the scope the app records for the session; declaring it here would put its whole
        // catalogue in front of the model on every turn — the very cost the annuaire exists to
        // avoid.
        .filter { (_, grant) -> grant.direct }

    val rules = mutableListOf(EnginePermissionRule(permission = ANY_TOOL, action = ACTION_DENY))
    catalogue.socle.forEach { (tool, action) ->
        if (namesOneTool(tool)) rules += EnginePermissionRule(permission = tool, action = action)
    }
    // The agent may ask the person (03/10/2026): somebody is watching every session this app builds
    // rules for, and the form answers. Not the catalogue's socle, which the scheduler's autonomous
    // missions also receive: a question nobody answers blocks a turn for good. The profile (`chat`,
    // `mission`) opens it server side; this rule keeps the session from closing it again.
    rules += EnginePermissionRule(permission = QUESTION_TOOL, action = ACTION_ALLOW)
    granted.flatMap { (_, grant) -> grant.outils }
        .distinct()
        .filter(::namesOneTool)
        .forEach { tool -> rules += EnginePermissionRule(permission = tool, action = ACTION_ALLOW) }
    return rules
}

/** A concrete tool name — not blank, no `*` or `?` — the only shape a catalogue pattern may take. */
private fun namesOneTool(pattern: String): Boolean {
    val concrete = pattern.isNotBlank() && '*' !in pattern && '?' !in pattern
    if (!concrete) Logger.w { "permissionsFor: ignored a catalogue pattern that is not one tool name" }
    return concrete
}

/**
 * [permissionsFor] read backwards: which connectors a ruleset actually grants.
 *
 * **The ruleset accumulates.** `PATCH /session/{id}` appends its rules; it does not replace them.
 * Measured 31/08/2026 on a live mission: **1 016 rules in 21 blocks**, each block a whole ruleset
 * opening with its own `*` → `deny`, growing 14 → 3 → 13 → 23 → … → 112 tools as connectors were
 * ticked one after another. A rule found anywhere in that list is therefore not a fact about the
 * session — reading it that way, as this did when it shipped that morning, reported every connector
 * ever ticked, including any since unticked.
 *
 * So only the **last block** is read: the rules after the final `*` → `deny`, which is precisely
 * the ruleset the last `PATCH` sent.
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
 * count against it; [rulesPredate] tells when they are missing, and the screen repairs the rules.
 *
 * `ask` and `deny` are not grants. A connector that declares no tool is never on.
 */
fun connectorsGranted(
    catalogue: ConnectorCatalogue,
    rules: List<EnginePermissionRule>,
): Set<String> {
    val lastBlock = rules.indexOfLast { it.permission == ANY_TOOL && it.action == ACTION_DENY }
    val standing = rules.drop(lastBlock + 1)
        .filter { it.action == ACTION_ALLOW }
        .map { it.permission }
        .toSet()
    return catalogue.connecteurs
        .filterValues { grant -> grant.outils.any { it in standing } }
        .keys
}

/**
 * Whether the standing block of [rules] misses a tool of a connector it grants: the catalogue
 * gained tools since the rules were written. A session in that state offers the model yesterday's
 * list; [permissionsFor] from the connectors read back writes today's.
 */
fun rulesPredate(
    catalogue: ConnectorCatalogue,
    rules: List<EnginePermissionRule>,
): Boolean {
    val lastBlock = rules.indexOfLast { it.permission == ANY_TOOL && it.action == ACTION_DENY }
    val standing = rules.drop(lastBlock + 1).filter { it.action == ACTION_ALLOW }.map { it.permission }.toSet()
    return connectorsGranted(catalogue, rules).any { name ->
        catalogue.connecteurs.getValue(name).outils.any { it !in standing }
    }
}

/** The one rule action that grants. */
private const val ACTION_ALLOW = "allow"

/** The catch-all every ruleset opens with, and so the marker of where the last one begins. */
private const val ACTION_DENY = "deny"
private const val ANY_TOOL = "*"

/** OpenCode's tool for asking the person a question, answered by the conversation's form. */
private const val QUESTION_TOOL = "question"

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
