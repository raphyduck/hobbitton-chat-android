package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry

interface FileRepository {
    suspend fun getFiles(): Result<List<FileObject>>
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        onProgress: ((Float) -> Unit)? = null,
    ): Result<FileObject>
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String? = null,
        endpoint: String? = null,
        model: String? = null,
        agentId: String? = null,
        toolResource: String? = null,
        messageFile: Boolean? = null,
        width: Int? = null,
        height: Int? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): Result<FileObject>
    suspend fun deleteFiles(
        files: List<DeleteFileEntry>,
        agentId: String? = null,
        toolResource: String? = null,
    ): Result<Unit>
    suspend fun downloadFile(userId: String, fileId: String): Result<ByteArray>
    suspend fun getAgentFiles(agentId: String): Result<List<FileObject>>
    suspend fun downloadCode(sessionId: String, fileId: String): Result<ByteArray>
}
