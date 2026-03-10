package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.FileObject
import com.librechat.android.core.model.request.DeleteFileEntry

interface FileRepository {
    suspend fun getFiles(): Result<List<FileObject>>
    suspend fun uploadFile(bytes: ByteArray, filename: String, type: String): Result<FileObject>
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String? = null,
        endpoint: String? = null,
        model: String? = null,
        agentId: String? = null,
        messageFile: Boolean? = null,
        width: Int? = null,
        height: Int? = null,
    ): Result<FileObject>
    suspend fun deleteFiles(files: List<DeleteFileEntry>): Result<Unit>
    suspend fun downloadFile(userId: String, fileId: String): Result<ByteArray>
    suspend fun getAgentFiles(agentId: String): Result<List<FileObject>>
    suspend fun downloadCode(sessionId: String, fileId: String): Result<ByteArray>
}
