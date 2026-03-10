package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Conversation

interface SearchRepository {
    suspend fun search(query: String): Result<List<Conversation>>
}
