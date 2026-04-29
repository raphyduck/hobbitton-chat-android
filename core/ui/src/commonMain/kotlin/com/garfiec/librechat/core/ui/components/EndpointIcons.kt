package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.ic_agents
import com.garfiec.librechat.core.ui.resources.ic_anthropic
import com.garfiec.librechat.core.ui.resources.ic_azure
import com.garfiec.librechat.core.ui.resources.ic_bedrock
import com.garfiec.librechat.core.ui.resources.ic_google
import com.garfiec.librechat.core.ui.resources.ic_openai
import org.jetbrains.compose.resources.painterResource

/**
 * Returns a Painter for the given endpoint's brand icon, or null if none is available.
 */
@Composable
fun endpointIconPainter(endpoint: EModelEndpoint): Painter? = when (endpoint) {
    EModelEndpoint.OPENAI -> painterResource(Res.drawable.ic_openai)
    EModelEndpoint.AZURE_OPENAI -> painterResource(Res.drawable.ic_azure)
    EModelEndpoint.GOOGLE -> painterResource(Res.drawable.ic_google)
    EModelEndpoint.ANTHROPIC -> painterResource(Res.drawable.ic_anthropic)
    EModelEndpoint.BEDROCK -> painterResource(Res.drawable.ic_bedrock)
    EModelEndpoint.AGENTS -> painterResource(Res.drawable.ic_agents)
    EModelEndpoint.ASSISTANTS -> painterResource(Res.drawable.ic_openai)
    EModelEndpoint.AZURE_ASSISTANTS -> painterResource(Res.drawable.ic_azure)
    EModelEndpoint.CUSTOM -> null
}

/**
 * String-based variant of [endpointIconPainter].
 */
@Composable
fun endpointIconPainter(endpoint: String?): Painter? = when (endpoint) {
    "openAI" -> painterResource(Res.drawable.ic_openai)
    "azureOpenAI" -> painterResource(Res.drawable.ic_azure)
    "google" -> painterResource(Res.drawable.ic_google)
    "anthropic" -> painterResource(Res.drawable.ic_anthropic)
    "bedrock" -> painterResource(Res.drawable.ic_bedrock)
    "agents" -> painterResource(Res.drawable.ic_agents)
    "assistants" -> painterResource(Res.drawable.ic_openai)
    "azureAssistants" -> painterResource(Res.drawable.ic_azure)
    else -> null
}

/**
 * Returns true if the endpoint icon is monochrome and should be tinted with
 * the theme's onSurfaceVariant color. Brand-colored icons (Anthropic, Google,
 * Azure, Bedrock) should be rendered with [Color.Unspecified] to preserve their colors.
 */
fun isMonochromeEndpointIcon(endpoint: String?): Boolean = when (endpoint) {
    "openAI", "assistants", "agents" -> true
    else -> false
}
