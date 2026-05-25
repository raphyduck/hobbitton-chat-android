package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.core.model.request.DeleteFilesRequest
import com.garfiec.librechat.core.network.api.FilesApi
import com.garfiec.librechat.core.network.api.FilesExtApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FileRepositoryImpl(
    private val filesApi: FilesApi,
    private val filesExtApi: FilesExtApi,
) : FileRepository {

    override suspend fun getFiles(): Result<List<FileObject>> =
        safeApiCall { filesApi.getFiles() }

    override suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        onProgress: ((Float) -> Unit)?,
    ): Result<FileObject> =
        safeApiCall {
            filesApi.uploadFile(
                bytes = bytes,
                filename = filename,
                type = type,
                // file_id and endpoint are required by the backend; provide defaults
                fileId = Uuid.random().toString(),
                endpoint = "agents",
                onProgress = onProgress,
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
        onProgress: ((Float) -> Unit)?,
    ): Result<FileObject> =
        safeApiCall {
            filesApi.uploadFile(
                bytes = bytes,
                filename = filename,
                type = type,
                fileId = fileId ?: Uuid.random().toString(),
                endpoint = endpoint,
                model = model,
                agentId = agentId,
                messageFile = messageFile,
                width = width,
                height = height,
                onProgress = onProgress,
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
