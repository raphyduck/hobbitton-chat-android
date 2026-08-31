package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineTime
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    @Test
    fun `what a session was granted is read back from the rules it carries`() {
        // The round trip is the contract: what the sheet ticked is what the chip must report, or
        // the conversation says « No connector » over a mission that is reading mail.
        val rules = permissionsFor(catalogue, listOf("memoire", "fichiers"), autonomous = false)

        assertEquals(setOf("memoire", "fichiers"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `a session granted nothing reads as nothing`() {
        val rules = permissionsFor(catalogue, emptyList(), autonomous = false)

        assertEquals(emptySet(), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `half a connector is not a connector`() {
        // `bash` alone is not `shell`. Reading « any tool allowed ⇒ connector on » would report a
        // capability the session does not have, which is the one direction this must never err in.
        val rules = listOf(EnginePermissionRule(permission = "bash", action = "allow"))

        assertEquals(emptySet(), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `only allow grants`() {
        // `ask` is a prompt, not a grant, and the ruleset always opens with a `*` deny — neither
        // may light a chip up.
        val rules = catalogue.connecteurs.getValue("fichiers").outils.map {
            EnginePermissionRule(permission = it, action = "ask")
        }

        assertEquals(emptySet(), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `only the last block is read, because PATCH appends`() {
        // Mesuré le 31/08/2026 : `PATCH /session/{id}` EMPILE ses règles. Une session vivante en
        // portait 1 016, en 21 blocs. Chercher un `allow` n'importe où rendait « accordé » tout
        // connecteur jamais coché — c'est ce que faisait la première version, livrée le matin même.
        val rules = permissionsFor(catalogue, listOf("memoire", "fichiers"), autonomous = false) +
            permissionsFor(catalogue, listOf("memoire"), autonomous = false)

        assertEquals(setOf("memoire"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `the last block counts whether it narrows or widens`() {
        val rules = permissionsFor(catalogue, listOf("memoire"), autonomous = false) +
            permissionsFor(catalogue, listOf("memoire", "fichiers"), autonomous = false)

        assertEquals(setOf("memoire", "fichiers"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `a connector that declares no tool is never on`() {
        // `containsAll` of an empty list is vacuously true, so without the guard every empty entry
        // in the catalogue would report as granted on a session that holds nothing at all.
        val empty = ConnectorCatalogue(connecteurs = mapOf("vide" to ConnectorGrant(outils = emptyList())))

        assertEquals(emptySet(), connectorsGranted(empty, emptyList()))
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
            "memoire" to ConnectorGrant(outils = listOf("memoire_lire"), tickedByDefault = true),
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
    fun `an option carries whether the scheduler ticks it by default`() {
        val offered = catalogue.offered(autonomous = false)

        // The socle is the SERVER's call, so the option only relays it — an app-side list of names
        // is exactly the copy that had the picker offering tools nobody serves.
        assertTrue(offered.single { it.name == "memoire" }.tickedByDefault)
        assertTrue(!offered.single { it.name == "shell" }.tickedByDefault)
    }

    @Test
    fun `a scheduler that predates the socle ticks nothing rather than everything`() {
        // The field defaults to false: an older scheduler serves no `defaut`, and the sheet must
        // open empty rather than pre-ticking the whole catalogue.
        val older = ConnectorCatalogue(
            connecteurs = mapOf("memoire" to ConnectorGrant(outils = listOf("memoire_lire"))),
        )

        assertTrue(older.offered(autonomous = false).none { it.tickedByDefault })
    }

    @Test
    fun `the options are ordered so the picker does not reshuffle between two openings`() {
        val names = catalogue.offered(autonomous = false).map { it.name }
        assertEquals(names.sorted(), names)
    }
}

/** Quand une mission a « bougé » pour la dernière fois — la clef de tri de la liste. */
class LastActivityTest {

    private fun session(created: Long? = null, updated: Long? = null) = EngineSession(
        id = "ses_1",
        time = if (created == null && updated == null) null else EngineTime(created = created, updated = updated),
    )

    private fun message(created: Long? = null, completed: Long? = null) = EngineMessage(
        info = EngineMessageInfo(
            id = "msg",
            role = "assistant",
            time = EngineTime(created = created, completed = completed),
        ),
    )

    @Test
    fun `the last message wins over the session's own dates`() {
        // C'est la demande : trier sur le dernier message, pas sur la naissance de la session.
        val activity = lastActivityOf(
            session(created = 1_000, updated = 2_000),
            listOf(message(created = 5_000), message(created = 9_000)),
        )

        assertEquals(9_000, activity)
    }

    @Test
    fun `a turn is dated by its end, not by its start`() {
        // Un tour d'assistant est créé quand il commence et complété quand il s'arrête : une mission
        // de dix minutes lancée à 03h00 a parlé pour la dernière fois à 03h10.
        assertEquals(600_000, lastActivityOf(session(created = 1), listOf(message(created = 1, completed = 600_000))))
    }

    @Test
    fun `an unfinished turn falls back to when it started`() {
        assertEquals(4_000, lastActivityOf(session(created = 1), listOf(message(created = 4_000))))
    }

    @Test
    fun `a running mission has no messages fetched and leans on the session`() {
        // Les messages d'une mission en cours ne sont délibérément pas récupérés (ils coûteraient
        // tout l'historique de l'onglet à chaque rafraîchissement) : `updated` prend le relais.
        assertEquals(2_000, lastActivityOf(session(created = 1_000, updated = 2_000), emptyList()))
    }

    @Test
    fun `without an updated date the creation date still sorts the row`() {
        // Mieux vaut une rangée mal datée qu'une rangée qui tombe au fond de la liste.
        assertEquals(1_000, lastActivityOf(session(created = 1_000), emptyList()))
    }

    @Test
    fun `a session the engine dates not at all is null rather than zero`() {
        assertNull(lastActivityOf(session(), emptyList()))
    }
}
