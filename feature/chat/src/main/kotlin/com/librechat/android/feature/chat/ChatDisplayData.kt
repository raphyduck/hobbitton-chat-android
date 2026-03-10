package com.librechat.android.feature.chat

import androidx.compose.runtime.Immutable

@Immutable
data class PresetDisplayData(
    val presetId: String?,
    val title: String,
    val endpointLabel: String?,
    val model: String?,
)

@Immutable
data class PromptMentionDisplayData(
    val name: String,
    val command: String?,
    val oneliner: String?,
)

@Immutable
data class McpServerDisplayData(
    val name: String,
    val title: String?,
    val description: String?,
    val isConnected: Boolean,
)
