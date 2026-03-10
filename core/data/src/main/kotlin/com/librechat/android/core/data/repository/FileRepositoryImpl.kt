package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.FileObject
import com.librechat.android.core.model.request.DeleteFileEntry
import com.librechat.android.core.model.request.DeleteFilesRequest
import com.librechat.android.core.network.api.FilesApi
import com.librechat.android.core.network.api.FilesExtApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val filesApi: FilesApi,
    private val filesExtApi: FilesExtApi,
) : FileRepository {

    override suspend fun getFiles(): Result<List<FileObject>> =
        safeApiCall { filesApi.getFiles() }

    override suspend fun uploadFile(bytes: ByteArray, filename: String, type: String): Result<FileObject> =
        safeApiCall {
            filesApi.uploadFile(
                bytes = bytes,
                filename = filename,
                type = type,
                // file_id and endpoint are required by the backend; provide defaults
                fileId = java.util.UUID.randomUUID().toString(),
                endpoint = "agents",
            )
        }

    override suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String?,
        endpoint: String?,
        model: String?,
        agentId: String?,
        messageFile: Boolean?,
        width: Int?,
        height: Int?,
    ): Result<FileObject> =
        safeApiCall {
            filesApi.uploadFile(
                bytes = bytes,
                filename = filename,
                type = type,
                fileId = fileId ?: java.util.UUID.randomUUID().toString(),
                endpoint = endpoint,
                model = model,
                agentId = agentId,
                messageFile = messageFile,
                width = width,
                height = height,
            )
        }

    override suspend fun deleteFiles(files: List<DeleteFileEntry>): Result<Unit> =
        safeApiCall { filesApi.deleteFiles(DeleteFilesRequest(files = files)) }

    override suspend fun downloadFile(userId: String, fileId: String): Result<ByteArray> =
        safeApiCall { filesApi.downloadFile(userId, fileId) }

    override suspend fun getAgentFiles(agentId: String): Result<List<FileObject>> =
        safeApiCall { filesExtApi.getAgentFiles(agentId) }

    override suspend fun downloadCode(sessionId: String, fileId: String): Result<ByteArray> =
        safeApiCall { filesExtApi.downloadCode(sessionId, fileId) }
}
