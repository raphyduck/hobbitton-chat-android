package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.Serializable

/**
 * Who is behind the portal, as the scheduler's `GET /identite` reports it from the headers the
 * edge's forward-auth copies (`Remote-User`, `Remote-Name`). Every field may be blank: an edge
 * that copies nothing, or a person with no display name.
 */
@Serializable
data class PortalIdentity(
    val utilisateur: String = "",
    val nom: String = "",
    val prenom: String = "",
) {
    /** The first name to greet with, or null when the portal gave none. */
    val firstName: String?
        get() = prenom.trim().ifBlank { nom.trim().substringBefore(' ') }.ifBlank { null }
}
