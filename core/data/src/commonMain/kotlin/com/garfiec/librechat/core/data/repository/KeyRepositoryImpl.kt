package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.UserKey
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.core.network.api.KeysApi

class KeyRepositoryImpl(
    private val keysApi: KeysApi,
) : KeyRepository {

    override suspend fun getKeyExpiry(): Result<List<UserKey>> = safeApiCall {
        keysApi.getKeyExpiry()
    }

    override suspend fun updateKey(request: UpdateKeyRequest): Result<UserKey> = safeApiCall {
        keysApi.updateKey(request)
    }

    override suspend fun deleteKey(name: String): Result<Unit> = safeApiCall {
        keysApi.deleteKey(name)
    }

    override suspend fun deleteAllKeys(): Result<Unit> = safeApiCall {
        keysApi.deleteAllKeys()
    }
}
