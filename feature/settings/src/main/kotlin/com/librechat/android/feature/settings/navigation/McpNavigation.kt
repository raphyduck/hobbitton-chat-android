package com.librechat.android.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.librechat.android.core.ui.components.ScreenTransitionWrapper
import com.librechat.android.feature.settings.screen.McpServersScreen

const val MCP_SERVERS_ROUTE = "settings/mcp"

fun NavGraphBuilder.mcpServersScreen(
    onNavigateBack: () -> Unit,
) {
    composable(MCP_SERVERS_ROUTE) {
        ScreenTransitionWrapper(transition) {
            McpServersScreen(
                onNavigateBack = onNavigateBack,
            )
        }
    }
}
