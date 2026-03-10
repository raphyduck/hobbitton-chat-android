package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.UserKey
import com.librechat.android.core.model.request.UpdateKeyRequest

interface KeyRepository {
    suspend fun getKeyExpiry(): Result<List<UserKey>>
    suspend fun updateKey(request: UpdateKeyRequest): Result<UserKey>
    suspend fun deleteKey(name: String): Result<Unit>
    suspend fun deleteAllKeys(): Result<Unit>
}
