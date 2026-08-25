package com.garfiec.librechat.core.data.engine

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
 * ## Ce qui reste du correctif du 24/08, et ce qui a disparu
 *
 * Ce qui reste : la **portée applicative**. Le `viewModelScope` meurt avec l'écran, et l'écran est
 * justement ce qui disparaît quand le navigateur passe devant. Le tour doit survivre au geste qui
 * l'a lancé, et son résultat attendre le retour — d'où le [StateFlow] plutôt qu'une valeur rendue à
 * l'appelant.
 *
 * Ce qui a disparu : le **service au premier plan**, et les deux permissions qui allaient avec. Il
 * n'existait que pour empêcher le gel du processus, parce qu'un `accept()` en attente ne s'exécute
 * plus une fois l'application mise en cache. Le retour du portail est maintenant un lien profond
 * (D-049) : le système **réveille** l'application pour le lui délivrer. Il n'y a plus rien à
 * maintenir éveillé.
 */
class EngineSignInCoordinator(
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
            val issue = runCatching { portail.signIn(ouvrirNavigateur) }
                .getOrElse { echec ->
                    Logger.w("Engine", echec) { "Le tour du portail a échoué d'entrée" }
                    EngineSignInResult.Interrupted(echec.message ?: "sign-in failed")
                }
            _etat.value = EngineSignInProgress.Termine(issue)
        }
    }

    /** Reprend l'état à zéro une fois le résultat lu, pour qu'un second essai reparte propre. */
    override fun acquitter() {
        if (_etat.value is EngineSignInProgress.Termine) {
            _etat.value = EngineSignInProgress.Idle
        }
    }
}
