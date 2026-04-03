package com.garfiec.librechat.feature.agents.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.garfiec.librechat.feature.agents.screen.AgentDetailScreen
import com.garfiec.librechat.feature.agents.screen.AgentEditorScreen
import com.garfiec.librechat.feature.agents.screen.AgentMarketplaceScreen

const val AGENTS_ROUTE = "agents"
const val AGENT_DETAIL_ROUTE = "agents/{agentId}"
const val AGENT_EDITOR_CREATE_ROUTE = "agents/editor/create"
const val AGENT_EDITOR_EDIT_ROUTE = "agents/editor/{agentId}"

fun NavGraphBuilder.agentsGraph(
    onAgentClick: (String) -> Unit,
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    onCreateAgent: () -> Unit,
    onEditAgent: (String) -> Unit,
) {
    composable(AGENTS_ROUTE) {
        AgentMarketplaceScreen(
            onAgentClick = onAgentClick,
            onCreateAgent = onCreateAgent,
            onBack = onBack,
        )
    }
    composable(
        route = AGENT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("agentId") { type = NavType.StringType },
        ),
    ) {
        AgentDetailScreen(
            onBack = onBack,
            onStartChat = onStartChat,
            onEdit = onEditAgent,
            onDuplicated = onAgentClick,
        )
    }
    composable(AGENT_EDITOR_CREATE_ROUTE) {
        AgentEditorScreen(
            onBack = onBack,
            onSaved = { agentId -> onAgentClick(agentId) },
        )
    }
    composable(
        route = AGENT_EDITOR_EDIT_ROUTE,
        arguments = listOf(
            navArgument("agentId") { type = NavType.StringType },
        ),
    ) {
        AgentEditorScreen(
            onBack = onBack,
            onSaved = { agentId -> onAgentClick(agentId) },
        )
    }
}
