package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.ApiKey
import com.librechat.android.core.model.request.CreateApiKeyRequest

interface ApiKeyRepository {
    suspend fun createApiKey(request: CreateApiKeyRequest): Result<ApiKey>
    suspend fun listApiKeys(): Result<List<ApiKey>>
    suspend fun getApiKey(id: String): Result<ApiKey>
    suspend fun deleteApiKey(id: String): Result<Unit>
}
