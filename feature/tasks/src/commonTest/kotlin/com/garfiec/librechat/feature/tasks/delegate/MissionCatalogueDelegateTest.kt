package com.garfiec.librechat.feature.tasks.delegate

import com.garfiec.librechat.core.data.engine.EngineModelChoice
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.network.engine.EngineHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Le chargement du catalogue partagé par la feuille « Nouvelle mission » et la conversation.
 *
 * Des faux en lambdas : le délégué ne connaît du dépôt que trois appels, c'est ce qui le rend
 * testable sans client HTTP.
 */
class MissionCatalogueDelegateTest {

    private val modele = EngineSelectableModel(providerId = "passerelle", modelId = "modele-a", label = "Modèle A")
    private val choix = EngineModelChoice(models = listOf(modele), preselected = modele)
    private val catalogue = ConnectorCatalogue(
        connecteurs = mapOf("memoire" to ConnectorGrant(outils = listOf("memoire_lire"))),
    )
    private val tarifs = ModelPrices(models = listOf(ModelPrice(model = "modele-a", input = 1.0, output = 2.0)))

    private fun delegue(
        models: suspend () -> EngineModelChoice = { choix },
        prices: suspend () -> ModelPrices = { tarifs },
        connectors: suspend () -> ConnectorCatalogue = { catalogue },
    ) = MissionCatalogueDelegate(fetchModels = models, fetchPrices = prices, fetchConnectors = connectors)

    @Test
    fun `ce qui se charge revient tel quel`() = runTest {
        val delegue = delegue()

        assertEquals(CatalogueFetch.Loaded(choix), delegue.models())
        assertEquals(CatalogueFetch.Loaded(catalogue), delegue.connectors())
        assertEquals(tarifs, delegue.prices())
    }

    @Test
    fun `un echec est classe, pas lance`() = runTest {
        val delegue = delegue(models = { throw EngineHttpException(401, "GET", "/config/providers") })

        // Le classement est celui de tout l'onglet : un 401 est une question de connexion, et
        // l'écran en tire son offre (se reconnecter), pas un bouton « réessayer ».
        assertEquals(CatalogueFetch.Failed(EngineFailureKind.AUTHENTICATION), delegue.models())
    }

    @Test
    fun `un hote en panne laisse l'autre selecteur debout`() = runTest {
        // Le planificateur tombe, le moteur répond : c'est la panne du 30/08/2026, où le sélecteur
        // de modèle disparaissait avec celui des connecteurs.
        val delegue = delegue(connectors = { throw EngineHttpException(503, "POST", "/mcp") })

        assertEquals(CatalogueFetch.Failed(EngineFailureKind.SERVER), delegue.connectors())
        assertEquals(CatalogueFetch.Loaded(choix), delegue.models())
    }

    @Test
    fun `une annulation n'est pas un echec`() = runTest {
        // Avalée, elle écrirait une erreur dans l'état d'un écran déjà quitté.
        val delegue = delegue(connectors = { throw CancellationException("écran quitté") })

        assertFailsWith<CancellationException> { delegue.connectors() }
    }

    @Test
    fun `les appels tournent dans le contexte donne`() = runTest {
        val vus = mutableListOf<String?>()
        val delegue = MissionCatalogueDelegate(
            fetchModels = { vus += currentCoroutineContext()[CoroutineName]?.name; choix },
            fetchPrices = { vus += currentCoroutineContext()[CoroutineName]?.name; tarifs },
            fetchConnectors = { vus += currentCoroutineContext()[CoroutineName]?.name; catalogue },
            context = CoroutineName("io"),
        )

        delegue.models()
        delegue.prices()
        delegue.connectors()

        assertEquals(listOf("io", "io", "io"), vus)
    }
}
