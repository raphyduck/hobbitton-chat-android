package com.librechat.android.core.ui.components

import androidx.annotation.DrawableRes
import com.librechat.android.core.model.EModelEndpoint
import com.librechat.android.core.ui.R

/**
 * Returns the bundled drawable resource for the given endpoint, or null if none is available.
 * Used by conversation list items and chat message avatars for provider-specific icons.
 */
@DrawableRes
fun EModelEndpoint.toIconRes(): Int? = when (this) {
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
 * Returns the drawable resource for an endpoint string (serial name), or null.
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

/**
 * Returns true if the endpoint icon is monochrome and should be tinted with
 * the theme's onSurfaceVariant color. Brand-colored icons (Anthropic, Google,
 * Azure, Bedrock) should be rendered with [Color.Unspecified] to preserve their colors.
 */
fun EModelEndpoint.isMonochromeIcon(): Boolean = when (this) {
    EModelEndpoint.OPENAI,
    EModelEndpoint.ASSISTANTS,
    EModelEndpoint.AGENTS,
    -> true
    else -> false
}

/**
 * String-based variant of [isMonochromeIcon].
 */
fun isMonochromeEndpointIcon(endpoint: String?): Boolean = when (endpoint) {
    "openAI", "assistants", "agents" -> true
    else -> false
}
