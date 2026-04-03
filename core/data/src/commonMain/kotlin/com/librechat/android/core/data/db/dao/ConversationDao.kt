package com.librechat.android.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.librechat.android.core.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE user = :userId AND isArchived = :isArchived ORDER BY updatedAt DESC LIMIT :limit")
    fun getConversations(userId: String, isArchived: Boolean = false, limit: Int = 25): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = :isArchived ORDER BY updatedAt DESC")
    fun getAllConversations(isArchived: Boolean = false): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversationId = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE conversationId = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE conversationId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations WHERE user = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE conversationId = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET isArchived = :isArchived, updatedAt = :updatedAt WHERE conversationId = :id")
    suspend fun updateArchived(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
