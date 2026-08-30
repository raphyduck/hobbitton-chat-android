package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the tab is allowed to grant a mission, and what it is allowed to claim about one.
 */
class PermissionsForTest {

    /**
     * A stand-in for what the scheduler serves. Deliberately holds the *real* tool names — the app's
     * old hand-written table named `read`/`write`/`edit` for `fichiers`, which the engine does not
     * serve, so every rule it built was inert and the mission ran with nothing. A fixture that
     * invented names would let that back in.
     */
    private val catalogue = ConnectorCatalogue(
        connecteurs = mapOf(
            "memoire" to ConnectorGrant(
                outils = listOf("memoire_lire", "memoire_lister", "memoire_interroger",
                    "memoire_rechercher", "memoire_retroliens"),
            ),
            "memoire-ecriture" to ConnectorGrant(outils = listOf("memoire_journaliser", "memoire_ecrire")),
            "fichiers" to ConnectorGrant(outils = listOf("fichiers_list_roots", "fichiers_read_text")),
            "shell" to ConnectorGrant(outils = listOf("bash", "lsp"), refusedWhenAutonomous = true),
        ),
        socle = mapOf("todowrite" to "allow"),
    )

    @Test
    fun `the rule list opens with a deny-all`() {
        val rules = permissionsFor(catalogue, listOf("memoire"), autonomous = true)

        // A profile is a ceiling; the checkboxes narrow it for this mission only. Starting from
        // « allow everything » and subtracting would turn a forgotten connector into a granted one.
        assertEquals("*", rules.first().permission)
        assertEquals("deny", rules.first().action)
    }

    @Test
    fun `a ticked connector opens exactly its own patterns`() {
        val rules = permissionsFor(catalogue, listOf("memoire"), autonomous = true)
        val allowed = rules.filter { it.action == "allow" }.map { it.permission }

        assertTrue(allowed.containsAll(listOf("memoire_lire", "memoire_rechercher")))
        // Exactly the catalogue's names — not a paraphrase of them.
        assertEquals(catalogue.connecteurs.getValue("memoire").outils.toSet(),
            allowed.toSet() - catalogue.socle.keys)
        // Reading memory is not writing to it: the two are separate connectors on the server side
        // and must stay separate here, or ticking « memory » would hand out write access.
        assertTrue(allowed.none { it.startsWith("memoire_ecrire") })
    }

    @Test
    fun `shell is refused to an autonomous mission`() {
        val rules = permissionsFor(catalogue, listOf("memoire", "shell"), autonomous = true)

        // Nobody is watching. An approval prompt nobody answers is not a safeguard — the mission
        // hangs until the watchdog kills it, and that is the *good* outcome.
        assertTrue(rules.none { it.permission == "bash" && it.action == "allow" })
    }

    @Test
    fun `shell is available to an interactive mission`() {
        val rules = permissionsFor(catalogue, listOf("shell"), autonomous = false)

        assertTrue(rules.any { it.permission == "bash" && it.action == "allow" })
    }

    @Test
    fun `an unknown connector grants nothing rather than everything`() {
        val rules = permissionsFor(catalogue, listOf("connecteur-invente"), autonomous = true)

        // A typo, a renamed connector, a newer server: none of them may end up widening access.
        assertEquals(listOf("*" to "deny", "todowrite" to "allow"), rules.map { it.permission to it.action })
    }

    @Test
    fun `no connectors at all is a mission that can only talk`() {
        val rules = permissionsFor(catalogue, emptyList(), autonomous = true)

        // The deny-all, plus the socle the engine grants every session on top of its connectors.
        // Dropping the socle builds incomplete rules, and silently.
        assertEquals(listOf("*" to "deny", "todowrite" to "allow"), rules.map { it.permission to it.action })
    }

    @Test
    fun `the same pattern reached twice is granted once`() {
        val rules = permissionsFor(catalogue, listOf("memoire", "memoire"), autonomous = true)

        assertEquals(rules.size, rules.distinctBy { it.permission }.size)
    }
}

/** The state shown next to a mission, on the payloads the engine actually produces. */
class MissionStateFromEngineTest {

    @Test
    fun `a running mission is not judged on its messages`() {
        // It has not finished; there is nothing to conclude yet, and fetching its messages to decide
        // would download history for an answer that is already known.
        val state = judgeRunning()

        assertIs<MissionState.Running>(state)
    }

    private fun judgeRunning(): MissionState =
        com.garfiec.librechat.core.model.engine.judgeMission(
            status = com.garfiec.librechat.core.model.engine.EngineSessionStatus(type = "running"),
            messages = emptyList(),
        )
}


/** What the pickers are allowed to offer, given what the platform declares. */
class ConnectorOptionsTest {

    private val catalogue = ConnectorCatalogue(
        connecteurs = mapOf(
            "shell" to ConnectorGrant(outils = listOf("bash", "lsp"), refusedWhenAutonomous = true),
            "memoire" to ConnectorGrant(outils = listOf("memoire_lire")),
        ),
    )

    @Test
    fun `an autonomous mission cannot tick what the platform bars it from`() {
        val shell = catalogue.offered(autonomous = true).single { it.name == "shell" }

        // Disabled, not absent: someone who wonders where shell went gets an answer.
        assertTrue(!shell.enabled)
        assertTrue(catalogue.offered(autonomous = true).any { it.name == "shell" })
    }

    @Test
    fun `a watched conversation may tick everything`() {
        assertTrue(catalogue.offered(autonomous = false).all { it.enabled })
    }

    @Test
    fun `each option carries what it costs`() {
        // Every tool a session declares is re-sent to the model on every turn (server-side D-040),
        // so the count is the price, shown where someone chooses to pay it.
        assertEquals(2, catalogue.offered(autonomous = false).single { it.name == "shell" }.toolCount)
    }

    @Test
    fun `the options are ordered so the picker does not reshuffle between two openings`() {
        val names = catalogue.offered(autonomous = false).map { it.name }
        assertEquals(names.sorted(), names)
    }
}
