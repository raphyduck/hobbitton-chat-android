package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The spending ceilings of the gateway's `chat` key, in dollars: what every conversation of the
 * app runs on since D-082. Read and written through the scheduler's `/plafonds` (12/10/2026),
 * which alone holds the gateway's master key.
 *
 * Three windows (D-069): the day and the week reopen by themselves and govern, the 30 days are a
 * net placed high enough never to be what cuts. A window the gateway does not report reads null.
 */
@Serializable
data class ChatBudget(
    @SerialName("jour") val day: Double? = null,
    @SerialName("semaine") val week: Double? = null,
    @SerialName("mois") val month: Double? = null,
)
