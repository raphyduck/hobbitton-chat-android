package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.core.model.FileReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-abstracted file attachment handling.
 * Android: wraps FileAttachmentDelegate with ContentResolver/Uri logic.
 * iOS: no-op initially (file attachments not yet supported on iOS).
 */
interface PlatformFileHandler {
    val attachedFiles: StateFlow<List<AttachedFile>>
    var pendingUploadSendJob: Job?

    fun onFilesSelected(platformRefs: List<Any>)
    fun removeFile(file: AttachedFile)
    fun retryUpload(file: AttachedFile)
    fun buildFileReferences(): List<FileReference>
    suspend fun waitForUploadsAndSend(text: String, doSend: (String) -> Unit)
    fun hasPendingUploads(): Boolean
    fun clearAttachedFiles()
}
