package com.librechat.android.feature.chat.viewmodel.delegate

import android.content.Context
import android.net.Uri
import com.librechat.android.core.common.EndpointConstants
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.FileRepository
import com.librechat.android.core.model.FileReference
import com.librechat.android.feature.chat.components.AttachedFile
import com.librechat.android.feature.chat.util.detectMimeTypeFromBytes
import com.librechat.android.feature.chat.util.fixFilenameExtension
import com.librechat.android.feature.chat.util.guessMimeType
import com.librechat.android.feature.chat.util.reEncodeImageIfNeeded
import com.librechat.android.feature.chat.util.resolveFileName
import com.librechat.android.feature.chat.viewmodel.ChatStateHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class FileAttachmentDelegate(
    private val stateHandle: ChatStateHandle,
    private val appContext: Context,
    private val fileRepository: FileRepository,
) {

    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    /** Job that waits for pending uploads before sending a message. */
    var pendingUploadSendJob: Job? = null

    /**
     * Called when the user selects files from Camera, Photos, or Files picker.
     * Immediately starts uploading each file to the server and tracks progress.
     */
    fun onFilesSelected(uris: List<Uri>) {
        uris.forEach { uri -> uploadFile(uri) }
    }

    private fun uploadFile(uri: Uri) {
        val context = appContext
        val contentResolver = context.contentResolver

        // Resolve filename and initial MIME type from URI metadata.
        // This is a preliminary type -- after reading bytes we'll verify it via magic bytes.
        val filename = resolveFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
        val preliminaryMimeType = contentResolver.getType(uri) ?: guessMimeType(filename)
        val isImage = preliminaryMimeType.startsWith("image/")

        Timber.d("uploadFile: uri=%s, filename=%s, preliminaryMimeType=%s, isImage=%s", uri, filename, preliminaryMimeType, isImage)

        // Add to the pending list with "uploading" state
        val pendingFile = AttachedFile(
            uri = uri,
            name = filename,
            isImage = isImage,
            uploadProgress = 0f,
            type = preliminaryMimeType,
        )

        _attachedFiles.update { currentList -> currentList + pendingFile }

        stateHandle.scope.launch {
            try {
                // Read file bytes
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    Timber.e("uploadFile: could not read bytes from URI: %s", uri)
                    markUploadFailed(uri)
                    stateHandle.update { copy(error = "Failed to upload $filename: Could not read file") }
                    return@launch
                }

                Timber.d("uploadFile: read %d bytes from %s", bytes.size, filename)

                // Detect actual MIME type from file content magic bytes.
                val detectedMimeType = detectMimeTypeFromBytes(bytes)
                var mimeType = if (detectedMimeType != null && detectedMimeType != preliminaryMimeType) {
                    Timber.w(
                        "uploadFile: MIME type mismatch for %s -- ContentResolver " +
                            "reported '%s' but actual content is '%s'. Using detected type.",
                        filename, preliminaryMimeType, detectedMimeType,
                    )
                    detectedMimeType
                } else {
                    preliminaryMimeType
                }

                // Re-encode images that are in formats the server may not handle well
                var uploadBytes = bytes
                val actualIsImage = mimeType.startsWith("image/")
                if (actualIsImage) {
                    val reEncoded = reEncodeImageIfNeeded(bytes, mimeType)
                    if (reEncoded != null) {
                        Timber.d(
                            "uploadFile: re-encoded image from %s to %s (%d -> %d bytes)",
                            mimeType, reEncoded.mimeType, bytes.size, reEncoded.bytes.size,
                        )
                        uploadBytes = reEncoded.bytes
                        mimeType = reEncoded.mimeType
                    }
                }

                // Rename the file extension to match the detected/re-encoded MIME type.
                val uploadFilename = fixFilenameExtension(filename, mimeType)
                if (uploadFilename != filename) {
                    Timber.d(
                        "uploadFile: renamed file from '%s' to '%s' to match MIME type '%s'",
                        filename, uploadFilename, mimeType,
                    )
                }

                // Update the attached file's type and name if detection/re-encoding changed them.
                val needsTypeUpdate = mimeType != preliminaryMimeType
                val needsNameUpdate = uploadFilename != filename
                if (needsTypeUpdate || needsNameUpdate) {
                    val isImageType = mimeType.startsWith("image/")
                    _attachedFiles.update { currentList ->
                        currentList.map { f ->
                            if (f.uri == uri) {
                                f.copy(
                                    type = mimeType,
                                    isImage = isImageType,
                                    name = uploadFilename,
                                )
                            } else {
                                f
                            }
                        }
                    }
                }

                // For images, resolve width and height so the server can process them correctly
                var imageWidth: Int? = null
                var imageHeight: Int? = null
                if (actualIsImage) {
                    try {
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeByteArray(uploadBytes, 0, uploadBytes.size, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            imageWidth = options.outWidth
                            imageHeight = options.outHeight
                        }
                        Timber.d("uploadFile: image dimensions %dx%d for %s", imageWidth, imageHeight, uploadFilename)
                    } catch (e: Exception) {
                        Timber.w(e, "uploadFile: could not read image dimensions for %s", uploadFilename)
                    }
                }

                // Update progress to 50% (upload starting)
                updateFileProgress(uri, 0.5f)

                // Generate a UUID for the file_id -- the backend requires this field
                val fileId = UUID.randomUUID().toString()

                // Upload to server with context about current endpoint/model
                val state = stateHandle.state
                val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
                Timber.d(
                    "uploadFile: sending to server -- fileId=%s, endpoint=%s, model=%s, isAgent=%s, mimeType=%s, filename=%s",
                    fileId, state.selectedEndpoint, state.selectedModel, isAgent, mimeType, uploadFilename,
                )
                val result = fileRepository.uploadFile(
                    bytes = uploadBytes,
                    filename = uploadFilename,
                    type = mimeType,
                    fileId = fileId,
                    endpoint = state.selectedEndpoint,
                    model = if (!isAgent) state.selectedModel else null,
                    agentId = if (isAgent) state.selectedModel else null,
                    messageFile = true,
                    width = imageWidth,
                    height = imageHeight,
                )

                when (result) {
                    is Result.Success -> {
                        val fileObject = result.data
                        Timber.d(
                            "uploadFile: success -- serverFileId=%s, filepath=%s, type=%s, %dx%d",
                            fileObject.fileId, fileObject.filepath, fileObject.type, fileObject.width, fileObject.height,
                        )
                        // Atomically update the attached file with server data
                        _attachedFiles.update { currentList ->
                            currentList.map { f ->
                                if (f.uri == uri) {
                                    f.copy(
                                        uploadProgress = 1f,
                                        fileId = fileObject.fileId,
                                        filepath = fileObject.filepath,
                                        type = fileObject.type,
                                        width = fileObject.width,
                                        height = fileObject.height,
                                    )
                                } else {
                                    f
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        Timber.e(result.exception, "uploadFile: server error -- %s", result.message)
                        // Atomically mark failed and remove from list in one update
                        _attachedFiles.update { currentList ->
                            currentList.map { f ->
                                if (f.uri == uri) {
                                    f.copy(uploadFailed = true, uploadProgress = null)
                                } else {
                                    f
                                }
                            }
                        }
                        stateHandle.update {
                            copy(error = "Failed to upload $filename: ${result.message ?: "Unknown error"}")
                        }
                    }
                    is Result.Loading -> {
                        Timber.w("uploadFile: unexpected Result.Loading received for %s", filename)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "uploadFile: unexpected exception for %s", filename)
                markUploadFailed(uri)
                stateHandle.update { copy(error = "Failed to upload $filename: ${e.message}") }
            }
        }
    }

    private fun updateFileProgress(uri: Uri, progress: Float) {
        _attachedFiles.update { currentList ->
            currentList.map { f ->
                if (f.uri == uri) f.copy(uploadProgress = progress) else f
            }
        }
    }

    private fun markUploadFailed(uri: Uri) {
        _attachedFiles.update { currentList ->
            currentList.map { f ->
                if (f.uri == uri) f.copy(uploadFailed = true, uploadProgress = null) else f
            }
        }
    }

    fun removeFile(file: AttachedFile) {
        _attachedFiles.update { currentList -> currentList.filter { it.uri != file.uri } }
    }

    fun retryUpload(file: AttachedFile) {
        _attachedFiles.update { currentList -> currentList.filter { it.uri != file.uri } }
        uploadFile(file.uri)
    }

    /**
     * Builds a list of [FileReference] from successfully uploaded attached files.
     * Only includes files that have a server-assigned fileId (upload completed).
     * Files still uploading (fileId == null) are excluded.
     */
    fun buildFileReferences(): List<FileReference> {
        val allFiles = _attachedFiles.value
        val uploadedFiles = allFiles.filter { it.fileId != null }
        val pendingFiles = allFiles.filter { it.fileId == null && !it.uploadFailed }
        if (pendingFiles.isNotEmpty()) {
            Timber.w(
                "buildFileReferences: %d file(s) still uploading and will NOT be included: %s",
                pendingFiles.size,
                pendingFiles.joinToString { it.name },
            )
        }
        Timber.d(
            "buildFileReferences: %d total, %d uploaded, %d pending, %d failed",
            allFiles.size, uploadedFiles.size, pendingFiles.size,
            allFiles.count { it.uploadFailed },
        )
        return uploadedFiles.map { file ->
            FileReference(
                fileId = file.fileId,
                filename = file.name,
                filepath = file.filepath,
                type = file.type,
                width = file.width,
                height = file.height,
            )
        }
    }

    /**
     * Waits for all pending file uploads to complete (up to 30 seconds),
     * then proceeds with sending the message via the provided callback.
     */
    suspend fun waitForUploadsAndSend(text: String, doSend: (String) -> Unit) {
        // Poll _attachedFiles until all files have either completed or failed.
        // Timeout after 30 seconds to avoid hanging forever.
        val timeoutMs = 30_000L
        val pollIntervalMs = 200L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            val pending = _attachedFiles.value.any { it.fileId == null && !it.uploadFailed }
            if (!pending) break
            delay(pollIntervalMs)
            elapsed += pollIntervalMs
        }
        val stillPending = _attachedFiles.value.count { it.fileId == null && !it.uploadFailed }
        if (stillPending > 0) {
            Timber.w("waitForUploadsAndSend: timed out with %d file(s) still uploading", stillPending)
        }
        doSend(text)
    }

    fun hasPendingUploads(): Boolean =
        _attachedFiles.value.any { it.fileId == null && !it.uploadFailed }

    fun clearAttachedFiles() {
        _attachedFiles.update { emptyList() }
    }
}
