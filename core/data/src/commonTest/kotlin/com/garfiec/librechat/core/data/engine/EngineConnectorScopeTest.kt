package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EnginePermissionRule
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
    fun `rules this app wrote, with a wildcard or by name, read back their connectors`() {
        // Until 10/11/2026 the app wrote the rules itself: by name from 09/11, opening with `*`
        // before. The conversations opened then still carry them until the first send has the
        // scheduler rewrite them.
        val withShell = ConnectorCatalogue(
            connecteurs = catalogue.connecteurs + ("shell" to ConnectorGrant(outils = listOf("bash", "lsp"))),
            socle = catalogue.socle,
        )
        val rules = legacyRules(withShell, listOf("memoire"))
        assertEquals(setOf("memoire"), connectorsGranted(withShell, rules))

        val before = listOf(
            EnginePermissionRule(permission = "*", action = "deny"),
            EnginePermissionRule(permission = "todowrite", action = "allow"),
            EnginePermissionRule(permission = "memoire_lire", action = "allow"),
            EnginePermissionRule(permission = "task", pattern = "eclaireur", action = "allow"),
        )
        assertEquals(setOf("memoire"), connectorsGranted(withShell, before))
        // Appended after it (PATCH accumulates), the new ruleset is the standing one.
        val appended = before + rules
        assertEquals(setOf("memoire"), connectorsGranted(withShell, appended))
    }

    @Test
    fun `a connector the annuaire serves is ticked by default and says so`() {
        val offered = catalogue.offered()

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

        val shell = catalogue.offered().single()
        assertFalse(shell.tickedByDefault)
        assertFalse(shell.viaAnnuaire)
    }

    @Test
    fun `the rules read back name only the direct connectors`() {
        val rules = legacyRules(catalogue, listOf("memoire", "qonto"))

        assertEquals(setOf("memoire"), connectorsGranted(catalogue, rules))
    }

    @Test
    fun `the direct perimeter follows the ticks, and without ticks opens every direct one but the shell`() {
        // Raphaël's rule (24/10/2026): the app chooses among the engine's direct connectors only;
        // the annuaire's are the scheduler's, whatever the ticks say.
        val catalogue = ConnectorCatalogue(
            connecteurs = mapOf(
                "memoire" to ConnectorGrant(outils = listOf("memoire_lire")),
                "planificateur" to ConnectorGrant(outils = listOf("planificateur_lister")),
                "shell" to ConnectorGrant(outils = listOf("bash")),
                "beeper" to ConnectorGrant(outils = listOf("beeper_envoyer"), direct = false),
            ),
        )
        assertEquals(listOf("memoire", "planificateur"), catalogue.directPerimeter(null))
        assertEquals(listOf("memoire", "shell"), catalogue.directPerimeter(listOf("memoire", "shell", "beeper")))
        assertEquals(emptyList(), catalogue.directPerimeter(emptyList()))
        assertEquals(listOf("memoire", "planificateur", "shell"), catalogue.directOffered().map { it.name })
    }
}
