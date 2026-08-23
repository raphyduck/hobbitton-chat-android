package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which model providers actually answer, and which no longer do.
 *
 * The one thing no spend counter can show: a dead provider costs nothing and does nothing, so a
 * revoked key or an exhausted credit is invisible until a mission fails at five in the morning.
 *
 * Obtaining this is **not free and not instant** — see [checkCost]. The screen must therefore make
 * it a deliberate action rather than something that fires on every refresh.
 */
@Serializable
data class ProviderHealth(
    @SerialName("fournisseurs") val providers: List<Provider> = emptyList(),
    /**
     * Roughly what one check costs, in dollars, as the server measured it — ten real calls capped
     * at sixteen tokens each. Shown to the person before they trigger it, because a button that
     * silently spends money is a button nobody should have to guess about.
     */
    @SerialName("cout_du_controle") val checkCost: Double = 0.0,
) {
    val allHealthy: Boolean get() = providers.isNotEmpty() && providers.all { it.isHealthy }
    val failing: List<Provider> get() = providers.filterNot { it.isHealthy }
}

@Serializable
data class Provider(
    @SerialName("nom") val name: String,
    /**
     * True only when **every** one of its models answers. A provider with one model down is not
     * healthy: the mission using that model will fail tonight, and calling it green because the
     * majority passes hides the outage behind an average.
     */
    @SerialName("sain") val isHealthy: Boolean = false,
    /** Where it is reached, when that is not the provider's default — null for Anthropic, set for
     * DeepSeek and Moonshot. It is what distinguishes a dead key from a moved endpoint. */
    @SerialName("adresse") val baseUrl: String? = null,
    @SerialName("modeles") val models: List<ProviderModel> = emptyList(),
)

@Serializable
data class ProviderModel(
    @SerialName("nom") val name: String,
    @SerialName("sain") val isHealthy: Boolean = false,
    /** The provider's own words — « Invalid API key », « insufficient balance ». That sentence is
     * what decides what to do next, so it is carried through rather than collapsed into a colour. */
    @SerialName("erreur") val error: String? = null,
    @SerialName("statut_http") val httpStatus: Int? = null,
)
