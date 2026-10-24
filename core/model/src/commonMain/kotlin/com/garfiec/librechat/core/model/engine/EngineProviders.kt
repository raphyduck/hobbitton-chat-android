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
 *
 * **This response carries a live credential, and nothing here decodes it.** Each provider object
 * has an `options` field holding the API key the engine was configured with — for the gateway
 * provider that is the LiteLLM virtual key, in clear text, to anyone who can reach the route.
 * Observed on the live engine, 28/08/2026. The classes below deliberately declare no `options`
 * field, so `ignoreUnknownKeys` drops it at parse and it never reaches app state; no HTTP client in
 * this module logs response bodies, so it never reaches Logcat either.
 *
 * Both of those are load-bearing. Adding an `options` property « for completeness », or turning a
 * client's `LogLevel` up to `BODY` while debugging, puts a gateway key into a log file.
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
    /**
     * What the model takes in and gives out, as the deployment declared it in `opencode.json`
     * (`input: ["text", "image", "pdf"]`). Null when it declared nothing: the engine then says
     * nothing about what the model sees, and the app assumes it sees everything rather than
     * warning about a model it knows nothing of.
     */
    val modalities: EngineModelModalities? = null,
)

@Serializable
data class EngineModelModalities(
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
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
    /**
     * False only when the deployment declared the model's input modalities and left `image` out
     * (24/10/2026: DeepSeek). The composer then warns before a photo leaves for it: the platform's
     * gateway describes the image in words for such a model, which is better than nothing but
     * less than seeing it.
     */
    val seesImages: Boolean = true,
) {
    val ref: EngineModelRef get() = EngineModelRef(providerId = providerId, modelId = modelId)
}
