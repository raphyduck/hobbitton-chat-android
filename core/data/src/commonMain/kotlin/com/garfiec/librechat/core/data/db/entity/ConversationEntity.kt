package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["isArchived", "updatedAt"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val conversationId: String,
    val title: String,
    val user: String,
    val endpoint: String?,
    val endpointType: String?,
    val model: String?,
    val agentId: String?,
    val isArchived: Boolean = false,
    val tags: String,
    val iconURL: String?,
    val greeting: String?,
    val modelParams: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = 0,
)
