package com.garfiec.librechat.core.network.api

/**
 * The scheduler's `/plafonds` said no, or answered something that is not a set of ceilings.
 *
 * Its own type for the same reason as [TranscriptionRefused]: the settings screen shows the
 * server's [reason] as it is (« le plafond du jour dépasse celui de la semaine »), and the
 * [status] tells amounts it will not take (400) from a caller it will not serve (403) and from a
 * gateway that did not apply them (502).
 */
class ChatBudgetRefused(
    val status: Int,
    val reason: String?,
    cause: Throwable? = null,
) : Exception("Chat budget refused with HTTP $status${reason?.let { ": $it" }.orEmpty()}", cause)
