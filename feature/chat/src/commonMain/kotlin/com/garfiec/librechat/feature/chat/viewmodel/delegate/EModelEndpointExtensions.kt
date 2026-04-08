package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.EModelEndpoint

internal fun EModelEndpoint.toSerialName(): String = when (this) {
    EModelEndpoint.AZURE_OPENAI -> "azureOpenAI"
    EModelEndpoint.OPENAI -> "openAI"
    EModelEndpoint.GOOGLE -> "google"
    EModelEndpoint.ANTHROPIC -> "anthropic"
    EModelEndpoint.ASSISTANTS -> "assistants"
    EModelEndpoint.AZURE_ASSISTANTS -> "azureAssistants"
    EModelEndpoint.AGENTS -> "agents"
    EModelEndpoint.CUSTOM -> "custom"
    EModelEndpoint.BEDROCK -> "bedrock"
}
