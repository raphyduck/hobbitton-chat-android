package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A ticked box means « in the mission's scope », not « in the model's tool list » (D-071): only
 * the direct connectors become permission rules, the rest is the annuaire's.
 */
class EngineConnectorScopeTest {

    private val catalogue = ConnectorCatalogue(
        connecteurs = mapOf(
            "memoire" to ConnectorGrant(outils = listOf("memoire_lire"), tickedByDefault = true),
            "annuaire" to ConnectorGrant(
                outils = listOf("planificateur_annuaire_chercher", "planificateur_annuaire_appeler"),
                tickedByDefault = true,
            ),
            "qonto" to ConnectorGrant(outils = listOf("qonto_qonto_list_transactions"), direct = false),
            "imap-envoi" to ConnectorGrant(outils = listOf("imap_imap_send_email"), direct = false),
        ),
        socle = mapOf("todowrite" to "allow"),
    )

    @Test
    fun `only direct connectors become rules`() {
        val rules = permissionsFor(catalogue, listOf("memoire", "qonto", "imap-envoi"), autonomous = false)

        val allowed = rules.filter { it.action == "allow" }.map { it.permission }
        assertEquals(listOf("todowrite", "memoire_lire"), allowed)
        assertFalse(allowed.any { it.startsWith("qonto") || it.startsWith("imap") })
    }

    @Test
    fun `a connector the annuaire serves is ticked by default and says so`() {
        val offered = catalogue.offered(autonomous = false)

        val qonto = offered.single { it.name == "qonto" }
        assertTrue(qonto.tickedByDefault)
        assertTrue(qonto.viaAnnuaire)

        val memoire = offered.single { it.name == "memoire" }
        assertTrue(memoire.tickedByDefault)
        assertFalse(memoire.viaAnnuaire)
    }

    @Test
    fun `a direct connector the scheduler does not tick stays unticked`() {
        val catalogue = ConnectorCatalogue(
            connecteurs = mapOf("shell" to ConnectorGrant(outils = listOf("bash"), tickedByDefault = false)),
        )

        val shell = catalogue.offered(autonomous = false).single()
        assertFalse(shell.tickedByDefault)
        assertFalse(shell.viaAnnuaire)
    }

    @Test
    fun `the rules read back name only the direct connectors`() {
        val rules = permissionsFor(catalogue, listOf("memoire", "qonto"), autonomous = false)

        assertEquals(setOf("memoire"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `a catalogue without the field declares everything ticked, as before D-071`() {
        val old = ConnectorCatalogue(
            connecteurs = mapOf("qonto" to ConnectorGrant(outils = listOf("qonto_qonto_list_transactions"))),
        )

        val allowed = permissionsFor(old, listOf("qonto"), autonomous = false)
            .filter { it.action == "allow" }
            .map { it.permission }
        assertEquals(listOf("qonto_qonto_list_transactions"), allowed)
        assertFalse(old.offered(autonomous = false).single().tickedByDefault)
    }
}
