package com.garfiec.librechat.feature.agents.components.model

data class AgentCapabilities(
    val artifacts: Boolean = false,
    val endAfterTools: Boolean = false,
    val hideSequentialOutputs: Boolean = false,
    val recursionLimit: Int = 25,
)
