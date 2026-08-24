package com.garfiec.librechat.core.data.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Le tour du portail, sorti de l'écran qui le déclenche.
 *
 * ## Ce qui a échoué, et ce que le détail disait
 *
 * Premier essai réel, le 24/08 : le navigateur atteint `127.0.0.1:34685/oauth/authelia` et reçoit
 * **« connection timed out »**. C'est ce mot-là qui informe — un port fermé répondrait *refused*,
 * immédiatement. Un délai d'attente veut dire que le socket est bien lié, que le noyau a accepté la
 * connexion dans sa file, et que **personne côté application n'est venu la chercher**.
 *
 * L'application ne s'exécutait donc plus pendant que la personne était dans son navigateur. Deux
 * raisons possibles, et ce coordinateur les couvre toutes les deux plutôt que de parier sur l'une :
 *
 *  * l'écran est détruit — le `viewModelScope` qui portait l'attente meurt avec lui. D'où la portée
 *    applicative ici : le tour survit à l'écran qui l'a lancé, et le résultat l'attend au retour.
 *  * le processus est **gelé** — Android met en cache une application passée en arrière-plan, et un
 *    `accept()` en attente ne s'exécute plus. Seul un service au premier plan en est exempté, d'où
 *    [EngineSignInService].
 *
 * ## Pourquoi la boucle locale, alors qu'elle coûte tout ça
 *
 * Parce qu'il n'y a pas d'autre forme. Vérifié contre `authelia validate-config` le 24/08 :
 *
 * ```
 * option 'response_modes' must only have the values 'form_post' and 'form_post.jwt'
 * when configured with scope 'authelia.bearer.authz' but the values 'query' are present
 * ```
 *
 * Et un `form_post` ne peut pas être délivré à un schéma d'application : une App Link perd le corps.
 */
class EngineSignInCoordinator(
    private val contexte: Context,
    private val portail: EngineSignIn,
    private val portee: CoroutineScope,
) : EngineSignInLauncher {

    private val _etat = MutableStateFlow<EngineSignInProgress>(EngineSignInProgress.Idle)

    /**
     * Où en est le tour. Un `StateFlow` plutôt qu'un résultat rendu à l'appelant : celui qui a lancé
     * la connexion n'est pas forcément là quand elle se termine, et c'est précisément le cas qu'on
     * répare.
     */
    override val etat: StateFlow<EngineSignInProgress> = _etat.asStateFlow()

    private var enCours: Job? = null

    /**
     * Lance le tour, ou ne fait rien s'il en reste un en vol.
     *
     * Le garde n'est pas une politesse : une seconde tentative ouvrirait un second onglet contre une
     * demande que la première a déjà consommée, et l'échec qui en résulte ne nomme ni l'une ni
     * l'autre.
     */
    override fun lancer(ouvrirNavigateur: (url: String) -> Unit) {
        if (enCours?.isActive == true) return

        enCours = portee.launch {
            _etat.value = EngineSignInProgress.EnCours
            ancrer()
            try {
                val issue = runCatching { portail.signIn(ouvrirNavigateur) }
                    .getOrElse { echec ->
                        Logger.w("Engine", echec) { "Le tour du portail a échoué d'entrée" }
                        EngineSignInResult.Interrupted(echec.message ?: "sign-in failed")
                    }
                _etat.value = EngineSignInProgress.Termine(issue)
            } finally {
                liberer()
            }
        }
    }

    /** Reprend l'état à zéro une fois le résultat lu, pour qu'un second essai reparte propre. */
    override fun acquitter() {
        if (_etat.value is EngineSignInProgress.Termine) {
            _etat.value = EngineSignInProgress.Idle
        }
    }

    /**
     * Démarre le service qui empêche le gel — et n'échoue jamais la connexion s'il est refusé.
     *
     * Android refuse un service au premier plan dans plusieurs situations (économiseur de batterie,
     * restrictions constructeur, démarrage depuis l'arrière-plan). Sans lui la connexion redevient
     * seulement fragile ; la faire échouer ici la rendrait impossible.
     */
    private fun ancrer() {
        runCatching {
            val intention = Intent(contexte, EngineSignInService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(contexte, intention)
            } else {
                contexte.startService(intention)
            }
        }.onFailure { echec ->
            Logger.i("Engine", echec) { "Pas de service au premier plan — le tour continue sans" }
        }
    }

    private fun liberer() {
        runCatching { contexte.stopService(Intent(contexte, EngineSignInService::class.java)) }
    }
}
