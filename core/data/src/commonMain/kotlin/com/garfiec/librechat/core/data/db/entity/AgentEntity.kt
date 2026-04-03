package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey
    val id: String,
    val name: String?,
    val description: String?,
    val avatar: String?,
    val provider: String,
    val model: String,
    val category: String?,
    val authorName: String?,
    val isPromoted: Boolean = false,
    val conversationStarters: String?,
    val tools: String?,
    val updatedAt: Long,
)
