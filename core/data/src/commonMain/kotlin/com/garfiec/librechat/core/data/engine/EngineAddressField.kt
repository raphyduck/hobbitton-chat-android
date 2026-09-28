package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.common.network.CleartextPolicy

/** The three addresses the app is pointed at, as a form names them. */
enum class EngineAddressField { BASE_URL, ISSUER_URL, SCHEDULER_URL }

/**
 * What an address form refuses to save — the one rule, shared by the sign-in screen (D-077) and
 * the Tasks tab's settings sheet, so the two cannot accept different things.
 *
 * Stricter than [com.garfiec.librechat.core.network.engine.EngineAccess.isConfigured], deliberately:
 * that one only asks whether the addresses are there, while this one asks whether they can *work*.
 * `agent.hobbitton.at` without a scheme is accepted by every text field and rejected by every HTTP
 * client, and the resulting « unknown host » reads as a network outage on a phone whose network is
 * fine.
 *
 * `http://` is accepted towards a private host only (finding F5, 26/09/2026): the portal's bearer
 * goes on every request to the engine and the scheduler, and the scheduler's address is where the
 * authorization code comes back. [CleartextPolicy] is the one definition of « private ».
 *
 * [schedulerRequired]: the sign-in needs the scheduler — the portal hands the code back through
 * it — while the Tasks tab's sheet has always accepted a blank one (« I do not have one »).
 */
fun validateEngineAddresses(
    baseUrl: String,
    issuerUrl: String,
    schedulerUrl: String,
    schedulerRequired: Boolean,
): Set<EngineAddressField> = buildSet {
    if (!baseUrl.isAcceptableAddress()) add(EngineAddressField.BASE_URL)
    if (!issuerUrl.isAcceptableAddress()) add(EngineAddressField.ISSUER_URL)
    val schedulerChecked = schedulerRequired || schedulerUrl.isNotBlank()
    if (schedulerChecked && !schedulerUrl.isAcceptableAddress()) add(EngineAddressField.SCHEDULER_URL)
}

private fun String.isAcceptableAddress(): Boolean {
    val trimmed = trim()
    val hasScheme = (trimmed.startsWith("https://") || trimmed.startsWith("http://")) &&
        trimmed.substringAfter("://").isNotBlank()
    return hasScheme && CleartextPolicy.isPermitted(trimmed)
}
