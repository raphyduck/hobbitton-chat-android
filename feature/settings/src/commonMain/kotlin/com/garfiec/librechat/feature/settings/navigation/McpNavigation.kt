package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.feature.settings.screen.McpServersScreen

const val MCP_SERVERS_ROUTE = "settings/mcp"

fun NavGraphBuilder.mcpServersScreen(
    onNavigateBack: () -> Unit,
) {
    composable(MCP_SERVERS_ROUTE) {
        McpServersScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
