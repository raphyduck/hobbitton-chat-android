package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
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
import com.librechat.android.core.network.api.AgentsApi
import kotlinx.serialization.json.Json

class AgentRepositoryImpl(
    private val agentsApi: AgentsApi,
) : AgentRepository {

    private var cachedAgents: List<Agent>? = null

    // --- Agent CRUD ---

    override suspend fun getAgents(category: String?): Result<List<Agent>> {
        return safeApiCall {
            if (category == null && cachedAgents != null) {
                return@safeApiCall cachedAgents!!
            }
            val agents = agentsApi.getAgents(category).data
            if (category == null) {
                cachedAgents = agents
            }
            agents
        }
    }

    override suspend fun getAgentsPaginated(
        page: Int,
        limit: Int,
        search: String?,
        category: String?,
    ): Result<PaginatedAgents> {
        return safeApiCall {
            val response = agentsApi.getAgentsPaginated(
                pageNumber = page,
                pageSize = limit,
                search = search,
                category = category,
            )
            PaginatedAgents(
                agents = response.data,
                hasMore = response.hasMore,
                total = response.data.size,
            )
        }
    }

    override suspend fun getAgent(id: String): Result<Agent> {
        return safeApiCall {
            cachedAgents?.find { it.id == id }?.let { return@safeApiCall it }
            agentsApi.getAgent(id)
        }
    }

    override suspend fun getAgentForEditing(id: String): Result<Agent> {
        // Always fetch from server via /expanded endpoint -- never use cache.
        // The cache and standard getAgent endpoint only have basic view fields.
        return safeApiCall {
            agentsApi.getAgentForEditing(id)
        }
    }

    override suspend fun createAgent(request: CreateAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.createAgent(request)
            invalidateCache()
            agent
        }
    }

    override suspend fun updateAgent(id: String, request: UpdateAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.updateAgent(id, request)
            invalidateCache()
            agent
        }
    }

    override suspend fun deleteAgent(id: String): Result<Unit> {
        return safeApiCall {
            agentsApi.deleteAgent(id)
            invalidateCache()
        }
    }

    override suspend fun duplicateAgent(id: String): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.duplicateAgent(id)
            invalidateCache()
            agent
        }
    }

    override suspend fun revertAgent(id: String, request: RevertAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.revertAgent(id, request)
            invalidateCache()
            agent
        }
    }

    override suspend fun getAgentExpanded(id: String): Result<AgentExpanded> {
        return safeApiCall {
            agentsApi.getAgentExpanded(id)
        }
    }

    override suspend fun getAgentCategories(): Result<List<AgentCategory>> {
        return safeApiCall {
            agentsApi.getAgentCategories()
        }
    }

    // --- Avatar ---

    override suspend fun uploadAgentAvatar(
        id: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.uploadAgentAvatar(id, imageBytes, mimeType)
            invalidateCache()
            agent
        }
    }

    // --- Actions ---

    override suspend fun getAgentActions(): Result<List<AgentAction>> {
        return safeApiCall {
            agentsApi.getAgentActions()
        }
    }

    override suspend fun addOrUpdateAction(
        agentId: String,
        request: CreateActionRequest,
    ): Result<Pair<Agent, AgentAction>> {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        return safeApiCall {
            val jsonArray = agentsApi.addOrUpdateAction(agentId, request)
            // Response is [Agent, Action] JsonArray
            if (jsonArray.size < 2) {
                throw IllegalStateException("Expected array of 2 elements for agent action response, got ${jsonArray.size}")
            }
            val agent = json.decodeFromJsonElement(Agent.serializer(), jsonArray[0])
            val action = json.decodeFromJsonElement(AgentAction.serializer(), jsonArray[1])
            invalidateCache()
            Pair(agent, action)
        }
    }

    override suspend fun deleteAction(agentId: String, actionId: String): Result<Unit> {
        return safeApiCall {
            agentsApi.deleteAction(agentId, actionId)
        }
    }

    // --- Tools ---

    override suspend fun getAvailableTools(): Result<List<AgentTool>> {
        return safeApiCall {
            agentsApi.getAvailableTools()
        }
    }

    override suspend fun getToolCalls(): Result<List<ToolCallRecord>> {
        return safeApiCall {
            agentsApi.getToolCalls()
        }
    }

    override suspend fun getToolAuthStatus(toolId: String): Result<ToolAuthStatus> {
        return safeApiCall {
            agentsApi.getToolAuthStatus(toolId)
        }
    }

    override suspend fun callTool(toolId: String, request: ToolCallRequest): Result<ToolCallResult> {
        return safeApiCall {
            agentsApi.callTool(toolId, request)
        }
    }

    private fun invalidateCache() {
        cachedAgents = null
    }
}
