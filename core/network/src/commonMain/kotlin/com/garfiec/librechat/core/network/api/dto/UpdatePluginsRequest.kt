package com.garfiec.librechat.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePluginsRequest(
    val plugins: List<String>,
)
