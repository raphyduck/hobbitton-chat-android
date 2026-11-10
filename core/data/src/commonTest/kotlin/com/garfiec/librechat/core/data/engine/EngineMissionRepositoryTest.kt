package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the screen claims about a conversation whose rules this app wrote itself, before the
 * scheduler took the rules over (10/11/2026): its connectors, read back from the standing block.
 */
class LegacyRulesReadTest {

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
    fun `what a session was granted is read back from the rules it carries`() {
        // The round trip is the contract: what the sheet ticked is what the chip must report, or
        // the conversation says « No connector » over a mission that is reading mail.
        val rules = legacyRules(catalogue, listOf("memoire", "fichiers"))

        assertEquals(setOf("memoire", "fichiers"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `a session granted nothing reads as nothing`() {
        val rules = legacyRules(catalogue, emptyList())

        assertEquals(emptySet(), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `one tool allowed is the connector, and the rest of it reads as owed`() {
        // Until 31/10/2026 `bash` alone was not `shell`: a half connector read as off, so that the
        // chip never overstated. But rules are written once and the catalogue grows: a connector
        // that gained a tool read as absent in every older session, and the next rewrite stripped
        // it whole (pieces_jointes, 30/10/2026). The tools a ruleset predates are never named in
        // it; they cannot count against it. The first send has the scheduler rewrite them whole.
        val rules = listOf(EnginePermissionRule(permission = "bash", action = "allow"))

        assertEquals(setOf("shell"), connectorsGranted(catalogue, rules))
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
        val rules = legacyRules(catalogue, listOf("memoire", "fichiers")) +
            legacyRules(catalogue, listOf("memoire"))

        assertEquals(setOf("memoire"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `the last block counts whether it narrows or widens`() {
        val rules = legacyRules(catalogue, listOf("memoire")) +
            legacyRules(catalogue, listOf("memoire", "fichiers"))

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
    fun `each option carries what it costs`() {
        // Every tool a session declares is re-sent to the model on every turn (server-side D-040),
        // so the count is the price, shown where someone chooses to pay it.
        assertEquals(2, catalogue.offered().single { it.name == "shell" }.toolCount)
    }

    @Test
    fun `an option carries whether the scheduler ticks it by default`() {
        val offered = catalogue.offered()

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

        assertTrue(older.offered().none { it.tickedByDefault })
    }

    @Test
    fun `what acts in the person's name is the scheduler's call, refusals to autonomy included`() {
        val served = ConnectorCatalogue(
            connecteurs = mapOf(
                "imap" to ConnectorGrant(outils = listOf("imap_get")),
                "imap-envoi" to ConnectorGrant(outils = listOf("imap_send"), acts = true),
                "shell" to ConnectorGrant(outils = listOf("bash"), refusedWhenAutonomous = true),
            ),
        ).offered().associate { it.name to it.actsForYou }

        // `imap-envoi` is not refused to an unwatched mission, and still sends mail in their name.
        assertEquals(mapOf("imap" to false, "imap-envoi" to true, "shell" to true), served)
    }

    @Test
    fun `the options are ordered so the picker does not reshuffle between two openings`() {
        val names = catalogue.offered().map { it.name }
        assertEquals(names.sorted(), names)
    }
}
