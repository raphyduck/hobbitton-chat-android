package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.UserKey
import com.librechat.android.core.model.request.UpdateKeyRequest
import com.librechat.android.core.network.api.KeysApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyRepositoryImpl @Inject constructor(
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
