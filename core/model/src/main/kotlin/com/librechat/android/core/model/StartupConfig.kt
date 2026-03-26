package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Immutable
@Serializable
data class StartupConfig(
    val appTitle: String = "LibreChat",
    val emailLoginEnabled: Boolean = true,
    val registrationEnabled: Boolean = false,
    val socialLoginEnabled: Boolean = false,
    val socialLogins: List<String>? = null,
    val googleLoginEnabled: Boolean = false,
    val githubLoginEnabled: Boolean = false,
    val discordLoginEnabled: Boolean = false,
    val facebookLoginEnabled: Boolean = false,
    val appleLoginEnabled: Boolean = false,
    val openidLoginEnabled: Boolean = false,
    val openidLabel: String? = null,
    val openidImageUrl: String? = null,
    val openidAutoRedirect: Boolean = false,
    val samlLoginEnabled: Boolean = false,
    val samlLabel: String? = null,
    val samlImageUrl: String? = null,
    val emailEnabled: Boolean = true,
    val passwordResetEnabled: Boolean = false,
    val publicSharedLinksEnabled: Boolean = false,
    val sharedLinksEnabled: Boolean = false,
    val serverDomain: String = "",
    val helpAndFaqURL: String? = null,
    val minPasswordLength: Int? = null,
    val showBirthdayIcon: Boolean = false,
    val modelSpecs: ModelSpecs? = null,
    @SerialName("interface") val interfaceConfig: InterfaceConfig? = null,
    val turnstile: TurnstileConfig? = null,
    val balance: BalanceConfig? = null,
    val analyticsGtmId: String? = null,
    val instanceProjectId: String? = null,
    val bundlerURL: String? = null,
    val staticBundlerURL: String? = null,
    val sharePointFilePickerEnabled: Boolean? = null,
    val sharePointBaseUrl: String? = null,
    val sharePointPickerGraphScope: String? = null,
    val sharePointPickerSharePointScope: String? = null,
    val openidReuseTokens: Boolean? = null,
    val conversationImportMaxFileSize: Long? = null,
    val webSearch: JsonObject? = null,
    val ldap: LdapConfig? = null,
    val customFooter: String? = null,
    /** Backend version, if the server includes it in the config response (not yet standard). */
    val version: String? = null,
)

@Immutable
@Serializable
data class ModelSpecs(
    val list: List<ModelSpec> = emptyList(),
)

@Serializable
data class ModelSpec(
    val name: String,
    val label: String? = null,
    val preset: Preset? = null,
    val iconURL: String? = null,
    val description: String? = null,
)

@Immutable
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

@Serializable
data class PrivacyPolicyConfig(
    val externalUrl: String? = null,
    val openNewTab: Boolean? = null,
)

@Serializable
data class TermsOfServiceConfig(
    val externalUrl: String? = null,
)

@Serializable
data class TurnstileConfig(
    val siteKey: String? = null,
    val options: TurnstileOptions? = null,
)

@Serializable
data class TurnstileOptions(
    val language: String? = null,
    val size: String? = null,
)

@Serializable
data class BalanceConfig(
    val enabled: Boolean = false,
    val startBalance: Long? = null,
    val autoRefillEnabled: Boolean = false,
    val refillIntervalValue: Int? = null,
    val refillIntervalUnit: String? = null,
    val refillAmount: Long? = null,
)

@Serializable
data class LdapConfig(
    val enabled: Boolean = false,
    val username: Boolean? = null,
)
