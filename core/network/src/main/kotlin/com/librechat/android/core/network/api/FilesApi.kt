package com.librechat.android.core.network.api

import com.librechat.android.core.model.FileObject
import com.librechat.android.core.model.request.DeleteFilesRequest
import com.librechat.android.core.model.response.FileUploadConfig
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
import timber.log.Timber
import java.util.UUID

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

    /**
     * Uploads a file to the server via multipart form data.
     *
     * The backend requires:
     * - `file` part: the binary file data with filename and content type
     * - `file_id`: a UUID identifying this upload (server replaces with its own, keeps ours as temp_file_id)
     * - `endpoint`: the endpoint context (e.g. "agents", "openAI") -- required by filterFile()
     *
     * Optional fields:
     * - `model`, `agent_id`, `tool_resource`, `message_file`, `width`, `height`
     */
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String = UUID.randomUUID().toString(),
        endpoint: String? = null,
        model: String? = null,
        agentId: String? = null,
        toolResource: String? = null,
        messageFile: Boolean? = null,
        width: Int? = null,
        height: Int? = null,
    ): FileObject {
        Timber.d(
            "uploadFile: filename=%s, type=%s, size=%d bytes, fileId=%s, endpoint=%s, model=%s, agentId=%s, messageFile=%s, width=%s, height=%s",
            filename, type, bytes.size, fileId, endpoint, model, agentId, messageFile, width, height,
        )

        val response: FileObject = client.submitFormWithBinaryData(
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${encodeFilename(filename)}\"")
                    append(HttpHeaders.ContentType, type)
                })
                // file_id is required by the backend's filterFile() -- must be a valid UUID
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

        Timber.d(
            "uploadFile success: fileId=%s, filepath=%s, type=%s, width=%s, height=%s",
            response.fileId, response.filepath, response.type, response.width, response.height,
        )
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
        /**
         * Encodes a filename for use in Content-Disposition header.
         * Ensures special characters don't break the multipart form boundary.
         */
        private fun encodeFilename(filename: String): String =
            filename.replace("\"", "\\\"")
    }
}
