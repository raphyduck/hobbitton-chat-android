package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.network.engine.EngineEventTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le flux du moteur est global et lourd : une conversation ouverte et le guetteur des questions en
 * lisaient chacun une copie, le téléphone téléchargeait tout le trafic du moteur deux fois
 * (26/10/2026). Avec une portée de partage, les deux lisent la même connexion.
 *
 * Le flux est lu sur le répartiteur d'entrées-sorties réel : on attend ses ouvertures, on ne les
 * avance pas.
 */
class EngineFeedSharingTest {

    /** Un transport qui compte ses ouvertures et ne rend jamais la main. */
    private class TransportCompteur : EngineEventTransport {
        @Volatile
        var ouvertures = 0
            private set

        override fun stream(): Flow<ByteArray> = flow {
            ouvertures++
            awaitCancellation()
        }
    }

    private val moteur = MockEngine { respond("{}", HttpStatusCode.OK) }

    private suspend fun attendre(transport: TransportCompteur, ouvertures: Int) {
        withTimeout(5_000) { while (transport.ouvertures < ouvertures) delay(10) }
        // Le temps qu'une seconde ouverture, s'il devait y en avoir une, se produise.
        delay(300)
    }

    @Test
    fun `la conversation et le guetteur partagent une seule connexion au flux`() = runBlocking {
        val transport = TransportCompteur()
        val partage = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = testMissionRepository(moteur, eventTransport = transport, sharingScope = partage)

        val conversation = repository.events("ses_1").launchIn(this)
        val guetteur = repository.allEvents().launchIn(this)
        attendre(transport, ouvertures = 1)

        assertEquals(1, transport.ouvertures)
        conversation.cancel()
        guetteur.cancel()
        partage.cancel()
    }

    @Test
    fun `sans portee de partage, chaque lecteur ouvre la sienne`() = runBlocking {
        val transport = TransportCompteur()
        val repository = testMissionRepository(moteur, eventTransport = transport)

        val conversation = repository.events("ses_1").launchIn(this)
        val guetteur = repository.allEvents().launchIn(this)
        attendre(transport, ouvertures = 2)

        assertEquals(2, transport.ouvertures)
        conversation.cancel()
        guetteur.cancel()
    }
}
