package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineStreamEvent

/**
 * Plie un script d'événements en un seul état — ce que le ViewModel fait événement par événement.
 *
 * Vivait dans le code principal, où rien ne l'appelait : le ViewModel plie l'historique par-dessus
 * l'état courant et le flux vivant un événement à la fois, jamais depuis un état vide. Les tests du
 * réducteur en ont besoin ; lui seul, non (D-076).
 */
internal fun missionChatFrom(events: List<EngineStreamEvent>): MissionChatState =
    events.fold(MissionChatState()) { state, event -> state.reduce(event) }
