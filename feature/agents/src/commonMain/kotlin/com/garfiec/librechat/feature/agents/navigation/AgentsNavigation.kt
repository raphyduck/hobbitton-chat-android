package com.garfiec.librechat.feature.agents.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.feature.agents.screen.AgentDetailScreen
import com.garfiec.librechat.feature.agents.screen.AgentEditorScreen
import com.garfiec.librechat.feature.agents.screen.AgentMarketplaceScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface AgentsRoute

@Serializable data object AgentMarketplace : AgentsRoute
@Serializable data class AgentDetail(val agentId: String) : AgentsRoute
@Serializable data object AgentEditorCreate : AgentsRoute
@Serializable data class AgentEditorEdit(val agentId: String) : AgentsRoute

fun NavGraphBuilder.agentsGraph(
    onAgentClick: (String) -> Unit,
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    onCreateAgent: () -> Unit,
    onEditAgent: (String) -> Unit,
) {
    composable<AgentMarketplace> {
        AgentMarketplaceScreen(
            onAgentClick = onAgentClick,
            onCreateAgent = onCreateAgent,
            onBack = onBack,
        )
    }
    composable<AgentDetail> {
        AgentDetailScreen(
            onBack = onBack,
            onStartChat = onStartChat,
            onEdit = onEditAgent,
            onDuplicated = onAgentClick,
        )
    }
    composable<AgentEditorCreate> {
        AgentEditorScreen(
            onBack = onBack,
            onSaved = { agentId -> onAgentClick(agentId) },
        )
    }
    composable<AgentEditorEdit> {
        AgentEditorScreen(
            onBack = onBack,
            onSaved = { agentId -> onAgentClick(agentId) },
        )
    }
}

val agentsSerializersModule = SerializersModule {
    polymorphic(AgentsRoute::class) {
        subclass(AgentMarketplace::class, AgentMarketplace.serializer())
        subclass(AgentDetail::class, AgentDetail.serializer())
        subclass(AgentEditorCreate::class, AgentEditorCreate.serializer())
        subclass(AgentEditorEdit::class, AgentEditorEdit.serializer())
    }
}
