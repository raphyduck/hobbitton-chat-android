package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EModelEndpoint {
    @SerialName("azureOpenAI")
    AZURE_OPENAI,

    @SerialName("openAI")
    OPENAI,

    @SerialName("google")
    GOOGLE,

    @SerialName("anthropic")
    ANTHROPIC,

    @SerialName("assistants")
    ASSISTANTS,

    @SerialName("azureAssistants")
    AZURE_ASSISTANTS,

    @SerialName("agents")
    AGENTS,

    @SerialName("custom")
    CUSTOM,

    @SerialName("bedrock")
    BEDROCK,
}
