package com.garfiec.librechat.feature.agents.components.model

data class AgentVersion(
    val version: Int,
    val updatedAt: String?,
    val isCurrent: Boolean = false,
)
