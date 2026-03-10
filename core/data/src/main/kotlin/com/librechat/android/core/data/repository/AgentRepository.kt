package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Agent
import com.librechat.android.core.model.AgentAction
import com.librechat.android.core.model.AgentCategory
import com.librechat.android.core.model.AgentExpanded
import com.librechat.android.core.model.AgentTool
import com.librechat.android.core.model.PaginatedAgents
import com.librechat.android.core.model.ToolAuthStatus
import com.librechat.android.core.model.ToolCallRecord
import com.librechat.android.core.model.ToolCallResult
import com.librechat.android.core.model.request.CreateActionRequest
import com.librechat.android.core.model.request.CreateAgentRequest
import com.librechat.android.core.model.request.RevertAgentRequest
import com.librechat.android.core.model.request.ToolCallRequest
import com.librechat.android.core.model.request.UpdateAgentRequest

interface AgentRepository {

    // Agent CRUD
    suspend fun getAgents(category: String? = null): Result<List<Agent>>
    suspend fun getAgentsPaginated(
        page: Int,
        limit: Int,
        search: String? = null,
        category: String? = null,
    ): Result<PaginatedAgents>
    suspend fun getAgent(id: String): Result<Agent>
    /**
     * Fetches complete agent data for editing via the /expanded endpoint.
     * Unlike [getAgent] which returns limited view-only fields, this method
     * returns the full agent document including instructions, tools, category,
     * conversation_starters, model_parameters, and all other configuration.
     * Never served from cache -- always fetches fresh from the server.
     */
    suspend fun getAgentForEditing(id: String): Result<Agent>
    suspend fun createAgent(request: CreateAgentRequest): Result<Agent>
    suspend fun updateAgent(id: String, request: UpdateAgentRequest): Result<Agent>
    suspend fun deleteAgent(id: String): Result<Unit>
    suspend fun duplicateAgent(id: String): Result<Agent>
    suspend fun revertAgent(id: String, request: RevertAgentRequest): Result<Agent>
    suspend fun getAgentExpanded(id: String): Result<AgentExpanded>
    suspend fun getAgentCategories(): Result<List<AgentCategory>>

    // Avatar
    suspend fun uploadAgentAvatar(id: String, imageBytes: ByteArray, mimeType: String): Result<Agent>

    // Actions
    suspend fun getAgentActions(): Result<List<AgentAction>>
    suspend fun addOrUpdateAction(agentId: String, request: CreateActionRequest): Result<Pair<Agent, AgentAction>>
    suspend fun deleteAction(agentId: String, actionId: String): Result<Unit>

    // Tools
    suspend fun getAvailableTools(): Result<List<AgentTool>>
    suspend fun getToolCalls(): Result<List<ToolCallRecord>>
    suspend fun getToolAuthStatus(toolId: String): Result<ToolAuthStatus>
    suspend fun callTool(toolId: String, request: ToolCallRequest): Result<ToolCallResult>
}
