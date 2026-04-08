package com.garfiec.librechat.feature.agents.components.model

data class AgentAdvancedSettings(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
)
