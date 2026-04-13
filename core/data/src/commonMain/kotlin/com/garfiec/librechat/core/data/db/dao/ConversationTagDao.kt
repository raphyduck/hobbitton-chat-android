package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationTagDao {
    @Query("SELECT * FROM conversation_tags ORDER BY position ASC")
    fun getAllTags(): Flow<List<ConversationTagEntity>>

    @Upsert
    suspend fun upsertAll(tags: List<ConversationTagEntity>)

    @Query("DELETE FROM conversation_tags")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(tags: List<ConversationTagEntity>) {
        deleteAll()
        upsertAll(tags)
    }
}
