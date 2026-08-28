package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.Serializable

/**
 * The answer of `GET /config/providers`: which providers this engine is wired to, and what each one
 * offers.
 *
 * **Not `GET /provider`.** That route exists and looks like the obvious one; it returns the engine's
 * entire built-in catalogue of every provider it has ever heard of — measured at **5,5 MB** against
 * the live server on 28/08/2026, versus 11,8 kB here. Downloading it onto a phone to fill a picker
 * with nine entries would be absurd, and the size is not the only reason: that catalogue lists
 * providers this deployment has no key for, so most of what it offers cannot be selected.
 */
@Serializable
data class EngineProviderCatalogue(
    val providers: List<EngineProvider> = emptyList(),
    /**
     * The model each provider falls back to, keyed by provider id. It is what the engine itself
     * would pick, so it is the only honest thing to preselect — anything else silently overrides a
     * deployment's own choice from a phone.
     */
    val default: Map<String, String> = emptyMap(),
)

@Serializable
data class EngineProvider(
    val id: String,
    val name: String? = null,
    /**
     * `config` for a provider this deployment declared in its own `opencode.json`, `custom` for one
     * OpenCode ships with. The distinction is what tells the platform's gateway apart from
     * OpenCode's own bundled endpoint — see [EngineSelectableModel].
     */
    val source: String? = null,
    val models: Map<String, EngineProviderModel> = emptyMap(),
)

@Serializable
data class EngineProviderModel(
    val id: String? = null,
    val name: String? = null,
)

/**
 * One line of the model picker: a provider and a model, already paired.
 *
 * The pair is the unit that matters — `claude-sonnet-5` alone means nothing to the engine, which
 * wants `providerID` and `modelID` both, and two providers may well expose the same model id.
 */
data class EngineSelectableModel(
    val providerId: String,
    val modelId: String,
    /** What to show. The engine's own label when it has one, the raw id otherwise. */
    val label: String,
) {
    val ref: EngineModelRef get() = EngineModelRef(providerId = providerId, modelId = modelId)
}
