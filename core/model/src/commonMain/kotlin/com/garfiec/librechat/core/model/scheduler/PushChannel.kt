package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.Serializable

/**
 * Where the server's push notifications are published (19/10/2026): the scheduler's
 * `GET /notifications`, the public address of its ntfy server and the topic. The app follows the
 * topic itself, so the person never installs or configures anything. Both blank on an instance
 * without ntfy.
 */
@Serializable
data class PushChannel(
    val url: String = "",
    val sujet: String = "",
) {
    val usable: Boolean get() = url.isNotBlank() && sujet.isNotBlank()

    /** The topic's JSON stream, from the message after [since] (an ntfy id or a duration). */
    fun streamUrl(since: String): String = "${url.trimEnd('/')}/$sujet/json?since=$since"
}
