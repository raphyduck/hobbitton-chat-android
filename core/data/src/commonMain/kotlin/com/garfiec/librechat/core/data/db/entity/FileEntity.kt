package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [
        Index("conversationId"),
        Index("messageId"),
    ],
)
data class FileEntity(
    @PrimaryKey
    val fileId: String,
    val user: String,
    val conversationId: String?,
    val messageId: String?,
    val filename: String,
    val filepath: String,
    val type: String,
    val bytes: Long,
    val source: String,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val localPath: String? = null,
)
