package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.feature.settings.screen.McpServersScreen
import kotlinx.serialization.Serializable

@Serializable data object McpServers : SettingsRoute

fun NavGraphBuilder.mcpServersScreen(
    onNavigateBack: () -> Unit,
) {
    composable<McpServers> {
        McpServersScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
