package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Memory
import com.librechat.android.core.model.MemoryPreferences
import com.librechat.android.core.model.request.CreateMemoryRequest
import com.librechat.android.core.model.request.UpdateMemoryPreferencesRequest
import com.librechat.android.core.model.request.UpdateMemoryRequest

/** Repository for user memory CRUD operations. Uses memory key as unique identifier. */
interface MemoryRepository {
    suspend fun getMemories(): Result<List<Memory>>
    suspend fun createMemory(request: CreateMemoryRequest): Result<Memory>
    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences>
    suspend fun updateMemory(key: String, request: UpdateMemoryRequest): Result<Memory>
    suspend fun deleteMemory(key: String): Result<Unit>
}
