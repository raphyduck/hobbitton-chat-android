package com.garfiec.librechat.core.ui.components

import androidx.annotation.DrawableRes
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.R

/**
 * Returns the bundled Android drawable resource for the given endpoint, or null.
 */
@DrawableRes
fun EModelEndpoint.toIconRes(): Int? = endpointIconRes(this)

@DrawableRes
private fun endpointIconRes(endpoint: EModelEndpoint): Int? = when (endpoint) {
    EModelEndpoint.OPENAI -> R.drawable.ic_openai
    EModelEndpoint.AZURE_OPENAI -> R.drawable.ic_azure
    EModelEndpoint.GOOGLE -> R.drawable.ic_google
    EModelEndpoint.ANTHROPIC -> R.drawable.ic_anthropic
    EModelEndpoint.BEDROCK -> R.drawable.ic_bedrock
    EModelEndpoint.AGENTS -> R.drawable.ic_agents
    EModelEndpoint.ASSISTANTS -> R.drawable.ic_openai
    EModelEndpoint.AZURE_ASSISTANTS -> R.drawable.ic_azure
    EModelEndpoint.CUSTOM -> null
}

/**
 * Returns the bundled Android drawable resource for the given endpoint string, or null.
 * Used by Android-only code paths that still pass `@DrawableRes Int?` through the component chain.
 */
@DrawableRes
fun endpointIconRes(endpoint: String?): Int? = when (endpoint) {
    "openAI" -> R.drawable.ic_openai
    "azureOpenAI" -> R.drawable.ic_azure
    "google" -> R.drawable.ic_google
    "anthropic" -> R.drawable.ic_anthropic
    "bedrock" -> R.drawable.ic_bedrock
    "agents" -> R.drawable.ic_agents
    "assistants" -> R.drawable.ic_openai
    "azureAssistants" -> R.drawable.ic_azure
    else -> null
}
