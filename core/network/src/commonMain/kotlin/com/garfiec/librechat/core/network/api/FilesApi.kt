package com.garfiec.librechat.core.network.api

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFilesRequest
import com.garfiec.librechat.core.model.response.FileUploadConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FilesApi constructor(
    private val client: HttpClient,
) {
    suspend fun getFiles(): List<FileObject> =
        client.get {
            url { path("api/files") }
        }.body()

    suspend fun getFileConfig(): FileUploadConfig =
        client.get {
            url { path("api/files/config") }
        }.body()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String = Uuid.random().toString(),
        endpoint: String? = null,
        model: String? = null,
        agentId: String? = null,
        toolResource: String? = null,
        messageFile: Boolean? = null,
        width: Int? = null,
        height: Int? = null,
    ): FileObject {
        Logger.d("FilesApi") {
            "uploadFile: filename=$filename, type=$type, size=${bytes.size} bytes, fileId=$fileId, " +
                "endpoint=$endpoint, model=$model, agentId=$agentId, messageFile=$messageFile, width=$width, height=$height"
        }

        val response: FileObject = client.submitFormWithBinaryData(
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${encodeFilename(filename)}\"")
                    append(HttpHeaders.ContentType, type)
                })
                append("file_id", fileId)
                if (endpoint != null) append("endpoint", endpoint)
                if (model != null) append("model", model)
                if (agentId != null) append("agent_id", agentId)
                if (toolResource != null) append("tool_resource", toolResource)
                if (messageFile != null) append("message_file", messageFile.toString())
                if (width != null) append("width", width.toString())
                if (height != null) append("height", height.toString())
            }
        ) {
            url { path("api/files") }
        }.body()

        Logger.d("FilesApi") {
            "uploadFile success: fileId=${response.fileId}, filepath=${response.filepath}, type=${response.type}, width=${response.width}, height=${response.height}"
        }
        return response
    }

    suspend fun deleteFiles(request: DeleteFilesRequest) {
        client.delete {
            url { path("api/files") }
            setBody(request)
        }
    }

    suspend fun downloadFile(userId: String, fileId: String): ByteArray =
        client.get {
            url { path("api/files/download/$userId/$fileId") }
        }.body()

    companion object {
        private fun encodeFilename(filename: String): String =
            filename.replace("\"", "\\\"")
    }
}
