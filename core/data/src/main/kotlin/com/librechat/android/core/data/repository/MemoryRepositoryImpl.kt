package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.Memory
import com.librechat.android.core.model.MemoryPreferences
import com.librechat.android.core.model.request.CreateMemoryRequest
import com.librechat.android.core.model.request.UpdateMemoryPreferencesRequest
import com.librechat.android.core.model.request.UpdateMemoryRequest
import com.librechat.android.core.network.api.MemoriesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoriesApi: MemoriesApi,
) : MemoryRepository {

    override suspend fun getMemories(): Result<List<Memory>> =
        safeApiCall { memoriesApi.getMemories() }

    override suspend fun createMemory(request: CreateMemoryRequest): Result<Memory> =
        safeApiCall { memoriesApi.createMemory(request) }

    override suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences> =
        safeApiCall { memoriesApi.updatePreferences(request) }

    override suspend fun updateMemory(key: String, request: UpdateMemoryRequest): Result<Memory> =
        safeApiCall { memoriesApi.updateMemory(key, request) }

    override suspend fun deleteMemory(key: String): Result<Unit> =
        safeApiCall { memoriesApi.deleteMemory(key) }
}
