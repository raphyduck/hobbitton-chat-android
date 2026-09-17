package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.sse.SseHttpStatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ce que devient le flux d'événements quand le réseau tombe longtemps.
 *
 * Le seul échec qu'un utilisateur ne peut pas voir est celui où l'écran cesse d'écouter sans le
 * dire : la mission continue côté moteur, l'appareil n'en reçoit plus rien, et ça se lit exactement
 * comme un agent qui a cessé de répondre. Signalé le 17/09/2026 sur une session de mission — elle
 * répondait ; il suffisait de la rouvrir pour que tout apparaisse d'un coup.
 */
class EngineStreamClientRetryTest {

    /** Un transport qui échoue à chaque tentative, et qui les compte. */
    private class TransportEnPanne(
        private val echec: () -> Throwable,
    ) : EngineEventTransport {
        var tentatives = 0
            private set

        override fun stream(): Flow<ByteArray> = flow {
            tentatives++
            throw echec()
        }
    }

    private val client = EngineStreamClient(EngineEventParser(Json))

    @Test
    fun `le flux continue d'essayer bien au-dela des essais rapides`() = runTest {
        // Cinq essais rapides, ~31 s de temporisation cumulée. Le comportement d'avant s'arrêtait
        // là, définitivement et sans un mot.
        val transport = TransportEnPanne { IOException("réseau coupé") }

        val collecte = backgroundScope.launch { client.connect("s-1", transport).collect { } }
        advanceTimeBy(FENETRE_LONGUE_MS)
        val apresUneLonguePanne = transport.tentatives
        collecte.cancel()

        // Le nombre exact dépend des pauses ; ce qui est tenu ici, c'est qu'il DÉPASSE le budget
        // rapide — donc que le flux est toujours vivant après une panne de plusieurs minutes.
        assertTrue(
            apresUneLonguePanne > ESSAIS_RAPIDES + 1,
            "le flux a renoncé après $apresUneLonguePanne tentative(s) : une panne qui passe " +
                "laisserait l'écran sourd pour de bon",
        )
    }

    @Test
    fun `un refus d_authentification arrete le flux, lui, et tout de suite`() = runTest {
        // La seule panne qu'un nouvel essai ne peut pas réparer : le jeton est à refaire, et
        // marteler la route ne ferait que brûler de la batterie sur un échec certain.
        val transport = TransportEnPanne { SseHttpStatusException(401) }

        val collecte = backgroundScope.launch { client.connect("s-1", transport).collect { } }
        advanceTimeBy(FENETRE_LONGUE_MS)
        collecte.cancel()

        assertEquals(1, transport.tentatives, "un 401 ne doit être tenté qu'une fois")
    }

    private companion object {
        /** Le budget d'essais rapides du client, avant sa pause. */
        const val ESSAIS_RAPIDES = 5

        /** Cinq minutes : plusieurs pauses franches, de quoi voir si le flux est encore là. */
        const val FENETRE_LONGUE_MS = 5L * 60 * 1000
    }
}
