package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.UserKey
import com.garfiec.librechat.core.model.request.UpdateKeyRequest

interface KeyRepository {
    suspend fun getKeyExpiry(): Result<List<UserKey>>
    suspend fun updateKey(request: UpdateKeyRequest): Result<UserKey>
    suspend fun deleteKey(name: String): Result<Unit>
    suspend fun deleteAllKeys(): Result<Unit>
}
