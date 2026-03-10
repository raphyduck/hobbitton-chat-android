package com.librechat.android.feature.agents.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.librechat.android.core.ui.components.ScreenTransitionWrapper
import com.librechat.android.feature.agents.screen.AgentDetailScreen
import com.librechat.android.feature.agents.screen.AgentEditorScreen
import com.librechat.android.feature.agents.screen.AgentMarketplaceScreen

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
        ScreenTransitionWrapper(transition) {
            AgentMarketplaceScreen(
                onAgentClick = onAgentClick,
                onCreateAgent = onCreateAgent,
            )
        }
    }
    composable(
        route = AGENT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("agentId") { type = NavType.StringType },
        ),
    ) {
        ScreenTransitionWrapper(transition) {
            AgentDetailScreen(
                onBack = onBack,
                onStartChat = onStartChat,
                onEdit = onEditAgent,
                onDuplicated = onAgentClick,
            )
        }
    }
    composable(AGENT_EDITOR_CREATE_ROUTE) {
        ScreenTransitionWrapper(transition) {
            AgentEditorScreen(
                onBack = onBack,
                onSaved = { agentId -> onAgentClick(agentId) },
            )
        }
    }
    composable(
        route = AGENT_EDITOR_EDIT_ROUTE,
        arguments = listOf(
            navArgument("agentId") { type = NavType.StringType },
        ),
    ) {
        ScreenTransitionWrapper(transition) {
            AgentEditorScreen(
                onBack = onBack,
                onSaved = { agentId -> onAgentClick(agentId) },
            )
        }
    }
}
