package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes a dynamic model parameter that can be configured per-endpoint.
 * Used by ModelParameterSheet to render appropriate controls.
 */
@Serializable
data class ParameterDefinition(
    val key: String,
    val label: String,
    val type: ParameterType,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val default: String? = null,
    val description: String? = null,
    val options: List<String>? = null,
)

@Serializable
enum class ParameterType {
    @SerialName("slider")
    SLIDER,

    @SerialName("dropdown")
    DROPDOWN,

    @SerialName("checkbox")
    CHECKBOX,

    @SerialName("text")
    TEXT,

    @SerialName("switch")
    SWITCH,

    @SerialName("textarea")
    TEXTAREA,

    @SerialName("tags")
    TAGS,
}
