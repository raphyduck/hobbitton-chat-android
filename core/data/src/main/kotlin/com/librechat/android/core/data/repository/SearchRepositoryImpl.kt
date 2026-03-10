package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.Conversation
import com.librechat.android.core.network.api.ConversationsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val conversationsApi: ConversationsApi,
) : SearchRepository {

    override suspend fun search(query: String): Result<List<Conversation>> =
        safeApiCall {
            val response = conversationsApi.getConversations(search = query)
            response.conversations
        }
}
