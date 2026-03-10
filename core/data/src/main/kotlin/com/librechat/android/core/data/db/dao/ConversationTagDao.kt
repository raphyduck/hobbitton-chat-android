package com.librechat.android.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.librechat.android.core.data.db.entity.ConversationTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationTagDao {
    @Query("SELECT * FROM conversation_tags WHERE user = :userId ORDER BY position ASC")
    fun getTagsForUser(userId: String): Flow<List<ConversationTagEntity>>

    @Upsert
    suspend fun upsert(tag: ConversationTagEntity)

    @Upsert
    suspend fun upsertAll(tags: List<ConversationTagEntity>)

    @Query("DELETE FROM conversation_tags WHERE id = :id")
    suspend fun deleteById(id: Long)
}
