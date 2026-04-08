package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class InterfaceConfig(
    val privacyPolicy: PrivacyPolicyConfig? = null,
    val termsOfService: TermsOfServiceConfig? = null,
    val endpointsMenu: Boolean = true,
    val modelSelect: Boolean = true,
    val parameters: Boolean = true,
    val presets: Boolean = true,
    val sidePanel: Boolean = true,
    val bookmarks: Boolean = true,
    val prompts: JsonElement? = null,
    val agents: JsonElement? = null,
    val multiConvo: Boolean = true,
    val memories: Boolean = true,
    val temporaryChat: Boolean = true,
    val runCode: Boolean = true,
    val webSearch: Boolean = true,
    val fileSearch: Boolean = true,
    val fileCitations: Boolean = true,
    val customWelcome: String? = null,
    val remoteAgents: JsonElement? = null,
)
