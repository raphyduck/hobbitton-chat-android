package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Prompt
import com.librechat.android.core.model.PromptGroup
import com.librechat.android.core.model.request.AddPromptToGroupRequest
import com.librechat.android.core.model.request.CreatePromptRequest
import com.librechat.android.core.model.request.UpdatePromptGroupRequest
import com.librechat.android.core.model.request.UpdatePromptTagRequest
import com.librechat.android.core.model.response.PromptGroupListResponse

interface PromptRepository {
    suspend fun getGroups(pageSize: Int = 10, cursor: String? = null): Result<PromptGroupListResponse>
    suspend fun getGroup(groupId: String): Result<PromptGroup>
    suspend fun create(request: CreatePromptRequest): Result<PromptGroup>
    suspend fun update(groupId: String, request: UpdatePromptGroupRequest): Result<PromptGroup>
    suspend fun delete(groupId: String): Result<Unit>
    suspend fun getPrompt(promptId: String): Result<Prompt>
    suspend fun deletePrompt(promptId: String): Result<Unit>
    suspend fun addPromptToGroup(groupId: String, request: AddPromptToGroupRequest): Result<Prompt>
    suspend fun updatePromptProductionTag(promptId: String, request: UpdatePromptTagRequest): Result<Prompt>
    suspend fun getPromptsByGroupId(groupId: String): Result<List<Prompt>>
}
