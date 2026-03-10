package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class FileObject(
    @SerialName("file_id") val fileId: String,
    @SerialName("temp_file_id") val tempFileId: String? = null,
    val filename: String,
    val filepath: String,
    val type: String,
    val bytes: Long,
    val source: String? = null,
    val user: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Immutable
@Serializable
data class FileReference(
    @SerialName("file_id") val fileId: String? = null,
    val filename: String? = null,
    val filepath: String? = null,
    val type: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bytes: Long? = null,
    val source: String? = null,
)

@Immutable
@Serializable
data class Attachment(
    @SerialName("file_id") val fileId: String? = null,
    val filename: String? = null,
    val filepath: String? = null,
    val type: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val toolCallId: String? = null,
    val expiresAt: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)
