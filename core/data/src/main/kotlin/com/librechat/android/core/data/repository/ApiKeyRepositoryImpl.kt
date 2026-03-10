package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.ApiKey
import com.librechat.android.core.model.request.CreateApiKeyRequest
import com.librechat.android.core.network.api.ApiKeysApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyRepositoryImpl @Inject constructor(
    private val apiKeysApi: ApiKeysApi,
) : ApiKeyRepository {

    override suspend fun createApiKey(request: CreateApiKeyRequest): Result<ApiKey> = safeApiCall {
        apiKeysApi.createApiKey(request)
    }

    override suspend fun listApiKeys(): Result<List<ApiKey>> = safeApiCall {
        apiKeysApi.listApiKeys()
    }

    override suspend fun getApiKey(id: String): Result<ApiKey> = safeApiCall {
        apiKeysApi.getApiKey(id)
    }

    override suspend fun deleteApiKey(id: String): Result<Unit> = safeApiCall {
        apiKeysApi.deleteApiKey(id)
    }
}
