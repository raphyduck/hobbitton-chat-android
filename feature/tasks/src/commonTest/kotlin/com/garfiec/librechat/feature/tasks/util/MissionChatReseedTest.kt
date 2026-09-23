package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ce qui rend le re-semis sûr — et ce qu'il répare.
 *
 * Le flux d'événements du moteur n'a PAS de curseur de reprise : une reconnexion repart de
 * « maintenant ». Tout ce qu'une mission produit pendant une coupure n'est donc jamais redélivré, et
 * l'écran resté ouvert garde son trou. Le seul recours était de sortir vers la liste et de revenir,
 * ce qui détruit le ViewModel et relit le transcript. Signalé par Raphaël le 21/09/2026.
 *
 * `refresh()` relit donc le transcript sur un écran ouvert. Ça ne vaut que si re-plier l'historique
 * par-dessus un état déjà rempli ne duplique rien : c'est ce que ces tests tiennent.
 */
class MissionChatReseedTest {

    private fun started(id: String, role: String) = EngineStreamEvent.MessageStarted(id, role)
    private fun part(msg: String, id: String, snapshot: EnginePartSnapshot) =
        EngineStreamEvent.PartUpdated(msg, id, snapshot)
    private fun delta(msg: String, id: String, text: String) =
        EngineStreamEvent.PartDelta(msg, id, "text", text)

    /** Un transcript tel que `engineHistoryEvents` le rejoue : des parts complètes, jamais de deltas. */
    private fun transcript() = listOf(
        started("msg_u", "user"),
        part("msg_u", "p0", EnginePartSnapshot(type = "text", text = "Fais le point")),
        started("msg_a", "assistant"),
        part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "Voici le point.")),
    )

    @Test
    fun `rejouer le transcript sur un etat deja rempli ne duplique rien`() {
        val uneFois = missionChatFrom(transcript())
        // Exactement ce que fait refresh() : replier l'historique PAR-DESSUS l'état courant.
        val deuxFois = transcript().fold(uneFois) { state, event -> state.reduce(event) }

        assertEquals(uneFois.turns.size, deuxFois.turns.size, "des tours ont été dupliqués")
        assertEquals(uneFois.turns[0].text(), deuxFois.turns[0].text())
        assertEquals(
            uneFois.turns[1].text(),
            deuxFois.turns[1].text(),
            "le texte a été concaténé au lieu d'être remplacé",
        )
    }

    @Test
    fun `le re-semis rattrape ce que la coupure du flux a emporte`() {
        // L'écran a vu le début, puis le flux est tombé : ni la suite, ni l'Idle n'arrivent.
        val ampute = missionChatFrom(
            listOf(
                started("msg_a", "assistant"),
                part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "")),
                delta("msg_a", "p1", "Voici "),
            ),
        )
        assertTrue(ampute.streaming, "sans Idle, le tour reste marqué en cours — c'est le symptôme")

        // Le moteur, lui, a fini. Le transcript porte la réponse entière.
        val complet = listOf(
            started("msg_a", "assistant"),
            part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "Voici le point.")),
        ).fold(ampute.copy(streaming = false)) { state, event -> state.reduce(event) }

        assertEquals("Voici le point.", complet.turns.single().text())
        assertFalse(complet.streaming, "le spinner doit s'éteindre : le transcript fait foi")
    }
}
