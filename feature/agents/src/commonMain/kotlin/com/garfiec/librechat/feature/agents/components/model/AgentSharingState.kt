package com.garfiec.librechat.feature.agents.components.model

@androidx.compose.runtime.Immutable
data class AgentSharingState(
    val visibility: AgentVisibility = AgentVisibility.PRIVATE,
    val isCollaborative: Boolean = false,
)
