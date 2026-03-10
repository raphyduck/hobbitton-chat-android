package com.librechat.android.core.network.api

import com.librechat.android.core.model.Prompt
import com.librechat.android.core.model.PromptGroup
import com.librechat.android.core.model.request.AddPromptToGroupRequest
import com.librechat.android.core.model.request.CreatePromptRequest
import com.librechat.android.core.model.request.UpdatePromptGroupRequest
import com.librechat.android.core.model.request.UpdatePromptTagRequest
import com.librechat.android.core.model.response.PromptGroupListResponse
import com.librechat.android.core.model.response.PromptListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path
import javax.inject.Inject

class PromptsApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun getPromptGroups(
        pageSize: Int = 10,
        cursor: String? = null,
        name: String? = null,
        category: String? = null,
    ): PromptGroupListResponse =
        client.get {
            url { path("api/prompts/groups") }
            parameter("pageSize", pageSize)
            cursor?.let { parameter("cursor", it) }
            name?.let { parameter("name", it) }
            category?.let { parameter("category", it) }
        }.body()

    suspend fun getPromptGroup(groupId: String): PromptGroup =
        client.get {
            url { path("api/prompts/groups/$groupId") }
        }.body()

    suspend fun createPrompt(prompt: CreatePromptRequest): PromptGroup =
        client.post {
            url { path("api/prompts") }
            setBody(prompt)
        }.body()

    suspend fun updatePromptGroup(groupId: String, update: UpdatePromptGroupRequest): PromptGroup =
        client.patch {
            url { path("api/prompts/groups/$groupId") }
            setBody(update)
        }.body()

    suspend fun deletePromptGroup(groupId: String) {
        client.delete {
            url { path("api/prompts/groups/$groupId") }
        }
    }

    /**
     * Get prompts with filtering. Uses query parameters for category, type, etc.
     */
    suspend fun getPrompts(
        category: String? = null,
        type: String? = null,
        pageNumber: Int = 1,
        pageSize: Int = 10,
    ): PromptListResponse =
        client.get {
            url { path("api/prompts") }
            category?.let { parameter("category", it) }
            type?.let { parameter("type", it) }
            parameter("pageNumber", pageNumber)
            parameter("pageSize", pageSize)
        }.body()

    /**
     * Get all prompts without pagination.
     */
    suspend fun getAllPrompts(): PromptListResponse =
        client.get {
            url { path("api/prompts/all") }
        }.body()

    /**
     * Add a prompt to an existing group.
     */
    suspend fun addPromptToGroup(groupId: String, request: AddPromptToGroupRequest): Prompt =
        client.post {
            url { path("api/prompts/groups/$groupId/prompts") }
            setBody(request)
        }.body()

    /**
     * Get a single prompt by ID.
     */
    suspend fun getPrompt(promptId: String): Prompt =
        client.get {
            url { path("api/prompts/$promptId") }
        }.body()

    /**
     * Delete a single prompt by ID.
     */
    suspend fun deletePrompt(promptId: String) {
        client.delete {
            url { path("api/prompts/$promptId") }
        }
    }

    /**
     * Update the production tag for a prompt.
     */
    suspend fun updatePromptProductionTag(promptId: String, request: UpdatePromptTagRequest): Prompt =
        client.patch {
            url { path("api/prompts/$promptId/tags/production") }
            setBody(request)
        }.body()

    /**
     * Get all prompts belonging to a group.
     * Backend returns a raw JSON array of Prompt objects.
     */
    suspend fun getPromptsByGroupId(groupId: String): List<Prompt> =
        client.get {
            url { path("api/prompts") }
            parameter("groupId", groupId)
        }.body()
}
