package com.librechat.android.core.data.repository

import kotlinx.coroutines.flow.Flow

interface DraftRepository {
    suspend fun getDraft(conversationId: String): String?
    suspend fun saveDraft(conversationId: String, text: String)
    suspend fun deleteDraft(conversationId: String)
    fun observeAllDrafts(): Flow<Map<String, String>>
}
