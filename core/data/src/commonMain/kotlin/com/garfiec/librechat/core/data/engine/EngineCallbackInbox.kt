package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import com.garfiec.librechat.core.network.engine.auth.parseCallbackUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Là où le lien profond du portail est déposé, et là où le tour de connexion vient le chercher.
 *
 * Ce que le socket local faisait, en une boîte aux lettres : l'application n'écoute plus rien, elle
 * **est réveillée** par le système quand le navigateur suit `at.hobbitton.chat://oauth`. C'est toute
 * la différence avec le 24/08, où le processus gelé ne venait plus chercher la connexion que le
 * noyau avait acceptée pour lui.
 *
 * Deux faces, séparées exprès : [EngineCallbackInbox] est ce que le tour de connexion attend,
 * [EngineCallbackDelivery] est ce que l'écran d'entrée alimente. La même instance porte les deux —
 * elle doit être un singleton, sinon l'un dépose dans une boîte que l'autre ne relève pas.
 */
interface EngineCallbackInbox {
    /**
     * Arme la réception pour un tour, et jette ce qui traînait.
     *
     * Le nettoyage n'est pas une politesse : un lien resté d'une tentative abandonnée porte le
     * `state` de CETTE tentative-là, et serait rejeté au contrôle — en faisant échouer la nouvelle
     * connexion pour une raison qui n'a rien à voir avec elle.
     */
    fun armer()

    /** Attend le retour du portail, ou rend [FormPostCallback.Malformed] si le délai passe. */
    suspend fun attendre(timeoutMillis: Long): FormPostCallback

    /** Referme. Un lien qui arrive après n'est plus attendu par personne. */
    fun liberer()
}

/** La face « dépôt », pour le point d'entrée de la plateforme qui reçoit le lien profond. */
interface EngineCallbackDelivery {
    /**
     * Dépose ce que le système a délivré. Rend `true` si quelqu'un l'attendait.
     *
     * Le retour n'est pas décoratif : un `false` veut dire « ce lien n'intéressait personne », et
     * c'est ce qui permet au point d'entrée de ne pas le confondre avec un lien traité.
     */
    fun deposer(uri: String): Boolean
}

/**
 * L'implémentation, en code commun parce qu'il n'y a plus rien de propre à la plateforme dedans.
 *
 * C'est le gain concret du passage en HTTPS : le socket était une classe JVM, donc une interface,
 * une implémentation Android, un service au premier plan et deux permissions. Ce qui reste est un
 * [CompletableDeferred] que les deux plateformes partagent.
 */
class EngineCallbackMailbox : EngineCallbackInbox, EngineCallbackDelivery {

    private var attente: CompletableDeferred<FormPostCallback>? = null

    override fun armer() {
        attente = CompletableDeferred()
    }

    override suspend fun attendre(timeoutMillis: Long): FormPostCallback {
        val boite = attente ?: return FormPostCallback.Malformed("inbox not armed")
        return withTimeoutOrNull(timeoutMillis) { boite.await() }
            ?: FormPostCallback.Malformed("timed out waiting for the portal")
    }

    override fun liberer() {
        attente = null
    }

    override fun deposer(uri: String): Boolean {
        val boite = attente
        if (boite == null) {
            // Ordinaire, pas alarmant : un lien rouvert depuis l'historique du navigateur, ou une
            // connexion dont le délai est passé. On le dit en `debug` et on n'en fait rien.
            Logger.d("Engine") { "Un retour de portail est arrivé sans que rien ne l'attende" }
            return false
        }
        // `complete` rend `false` si la boîte est déjà servie : deux liens pour un tour, dont le
        // second n'a pas à écraser le premier.
        return boite.complete(parseCallbackUri(uri))
    }
}
