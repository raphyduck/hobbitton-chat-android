package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * La boîte aux lettres du lien profond.
 *
 * Ce qu'elle remplace est un socket, et l'échec du 24/08 était précisément un rendez-vous manqué
 * entre celui qui dépose et celui qui relève. Ces tests portent donc sur le rendez-vous, pas sur
 * l'analyse du lien — celle-là est chez `parseCallbackUri`.
 */
class EngineCallbackMailboxTest {

    private val lien = "at.hobbitton.chat://oauth?code=abc&state=xyz"

    @Test
    fun `un lien depose avant l'attente est quand meme releve`() = runTest {
        // Le cas rapide et réel : session du portail déjà valide, second facteur mémorisé, et le
        // retour arrive avant que la coroutine du tour n'atteigne `attendre`. Une boîte qui ne
        // retiendrait pas le dépôt raterait exactement les connexions les plus fluides.
        val boite = EngineCallbackMailbox()
        boite.armer()

        assertTrue(boite.deposer(lien))

        assertEquals(FormPostCallback.Success("abc", "xyz"), boite.attendre(1_000))
    }

    @Test
    fun `l'attente est debloquee par le depot`() = runTest {
        val boite = EngineCallbackMailbox()
        boite.armer()

        val attente = async { boite.attendre(60_000) }
        yield()
        boite.deposer(lien)

        assertEquals(FormPostCallback.Success("abc", "xyz"), attente.await())
    }

    @Test
    fun `sans depot le delai finit par rendre la main`() = runTest {
        // Cinq minutes en vrai. Sans borne, un tour abandonné dans le navigateur laisserait l'écran
        // sur « connexion en cours » jusqu'à la mort du processus.
        val boite = EngineCallbackMailbox()
        boite.armer()

        assertIs<FormPostCallback.Malformed>(boite.attendre(50))
    }

    @Test
    fun `un lien qui arrive alors que rien ne l'attend est ignore`() = runTest {
        // Un onglet rouvert depuis l'historique du navigateur, ou une connexion dont le délai est
        // passé. Le `false` est ce qui permet au point d'entrée de ne pas le confondre avec un lien
        // traité.
        val boite = EngineCallbackMailbox()

        assertFalse(boite.deposer(lien))
    }

    @Test
    fun `un second lien n'ecrase pas le premier`() = runTest {
        val boite = EngineCallbackMailbox()
        boite.armer()
        boite.deposer(lien)

        assertFalse(boite.deposer("at.hobbitton.chat://oauth?code=autre&state=xyz"))
        assertEquals(FormPostCallback.Success("abc", "xyz"), boite.attendre(1_000))
    }

    @Test
    fun `armer jette ce qu'un tour abandonne avait laisse`() = runTest {
        // Un lien resté d'une tentative abandonnée porte le `state` de CETTE tentative-là. Le
        // garder ferait échouer la nouvelle connexion au contrôle du `state`, pour une raison qui
        // n'a rien à voir avec elle — et le message parlerait du portail.
        val boite = EngineCallbackMailbox()
        boite.armer()
        boite.deposer("at.hobbitton.chat://oauth?code=vieux&state=ancien")

        boite.armer()

        assertIs<FormPostCallback.Malformed>(boite.attendre(50))
    }

    @Test
    fun `une boite liberee n'attend plus rien`() = runTest {
        val boite = EngineCallbackMailbox()
        boite.armer()
        boite.liberer()

        assertFalse(boite.deposer(lien))
        assertIs<FormPostCallback.Malformed>(boite.attendre(1_000))
    }
}
