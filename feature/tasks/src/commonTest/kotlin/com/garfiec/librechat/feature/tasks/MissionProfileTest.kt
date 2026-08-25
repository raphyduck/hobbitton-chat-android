package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le profil d'une mission lancée depuis l'application, choisi sans demander.
 *
 * Ce que ces tests épinglent n'est pas un détail d'affichage : le 25/08, la feuille « nouvelle
 * mission » offrait `compaction` — le résumeur interne d'OpenCode — comme profil, en première
 * position pour peu que l'ordre du moteur s'y prête. Une mission lancée dessus aurait tourné sur
 * l'invite d'un outil de compression de contexte.
 */
class MissionProfileTest {

    private fun profil(
        name: String,
        native: Boolean = false,
        hidden: Boolean = false,
        mode: String = "primary",
    ) = EngineAgentProfile(name = name, native = native, hidden = hidden, mode = mode)

    @Test
    fun `le profil generique gagne des qu'il est deploye`() {
        val liste = listOf(profil("build", native = true), profil("veille"), profil("mission"))

        assertEquals("mission", missionProfile(liste))
    }

    @Test
    fun `un moteur pas encore a jour retombe sur un profil declare, jamais un natif`() {
        // `build` et `compaction` viennent d'OpenCode, pas de la configuration du déploiement.
        // Les choisir ferait tourner la mission sur l'invite d'un agent de code ou d'un résumeur.
        val liste = listOf(
            profil("build", native = true),
            profil("compaction", native = true, hidden = true),
            profil("veille"),
        )

        assertEquals("veille", missionProfile(liste))
    }

    @Test
    fun `un sous-agent n'est pas un profil de mission`() {
        val liste = listOf(profil("explore", mode = "subagent"), profil("cerveau"))

        assertEquals("cerveau", missionProfile(liste))
    }

    @Test
    fun `sans aucune liste, on envoie le nom generique et on laisse le moteur repondre`() {
        // Le moteur refusera avec une erreur que l'écran sait montrer — ce qui vaut mieux qu'un
        // bouton « Lancer » qui ne peut rien lancer.
        assertEquals("mission", missionProfile(emptyList()))
    }
}
