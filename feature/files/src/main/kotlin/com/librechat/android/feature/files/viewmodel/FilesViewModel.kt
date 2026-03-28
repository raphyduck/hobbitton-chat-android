package com.librechat.android.feature.files.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.repository.FileRepository
import com.librechat.android.core.model.FileObject
import com.librechat.android.core.model.request.DeleteFileEntry
import com.librechat.android.feature.files.FileDisplayData
import com.librechat.android.feature.files.FilePreviewDisplayData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

enum class FileTypeFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    VIDEO("Video"),
}

enum class FileSortField(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    TYPE("Type"),
}

enum class FileSortOrder {
    ASCENDING,
    DESCENDING,
}

enum class FileViewMode {
    LIST,
    GRID,
}

@Immutable
data class FilesUiState(
    val displayFiles: List<FileDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadFilename: String = "",
    val uploadProgress: Float? = null,
    val error: String? = null,
    val selectedFilter: FileTypeFilter = FileTypeFilter.ALL,
    val isSelectionMode: Boolean = false,
    val selectedFileIds: Set<String> = emptySet(),
    val sortField: FileSortField = FileSortField.DATE,
    val sortOrder: FileSortOrder = FileSortOrder.DESCENDING,
    val previewFile: FilePreviewDisplayData? = null,
    val hasFiles: Boolean = false,
    val viewMode: FileViewMode = FileViewMode.LIST,
)

class FilesViewModel(
    private val fileRepository: FileRepository,
    private val context: Application,
    private val serverDataStore: ServerDataStore,
) : ViewModel() {

    private val _files = MutableStateFlow<List<FileObject>>(emptyList())
    private val _selectedFilter = MutableStateFlow(FileTypeFilter.ALL)
    private val _sortField = MutableStateFlow(FileSortField.DATE)
    private val _sortOrder = MutableStateFlow(FileSortOrder.DESCENDING)

    private val _transientState = MutableStateFlow(TransientState())

    /** Cache display data by fileId to avoid re-running Formatter.formatShortFileSize on every emission. */
    private val displayDataCache = mutableMapOf<String, FileDisplayData>()

    val uiState: StateFlow<FilesUiState> = combine(
        _files,
        _selectedFilter,
        _sortField,
        _sortOrder,
        _transientState,
    ) { files, filter, sortField, sortOrder, transient ->
        val filtered = filterFiles(files, filter)
        val sorted = sortFiles(filtered, sortField, sortOrder)
        val displayFiles = sorted.map { file ->
            displayDataCache.getOrPut(file.fileId) { file.toDisplayData() }
        }

        FilesUiState(
            displayFiles = displayFiles,
            isLoading = transient.isLoading,
            isRefreshing = transient.isRefreshing,
            isUploading = transient.isUploading,
            uploadFilename = transient.uploadFilename,
            uploadProgress = transient.uploadProgress,
            error = transient.error,
            selectedFilter = filter,
            isSelectionMode = transient.isSelectionMode,
            selectedFileIds = transient.selectedFileIds,
            sortField = sortField,
            sortOrder = sortOrder,
            previewFile = transient.previewFile,
            hasFiles = files.isNotEmpty(),
            viewMode = transient.viewMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FilesUiState(),
    )

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            updateTransient { copy(isLoading = true, error = null) }
            when (val result = fileRepository.getFiles()) {
                is Result.Success -> {
                    displayDataCache.clear()
                    _files.value = result.data
                    updateTransient { copy(isLoading = false) }
                }
                is Result.Error -> {
                    updateTransient {
                        copy(
                            isLoading = false,
                            error = result.message ?: "Failed to load files",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateTransient { copy(isRefreshing = true) }
            when (val result = fileRepository.getFiles()) {
                is Result.Success -> {
                    displayDataCache.clear()
                    _files.value = result.data
                    updateTransient { copy(isRefreshing = false) }
                }
                is Result.Error -> {
                    updateTransient {
                        copy(
                            isRefreshing = false,
                            error = result.message ?: "Failed to refresh files",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private var uploadJob: kotlinx.coroutines.Job? = null

    fun uploadFile(uri: Uri) {
        uploadJob = viewModelScope.launch {
            val filename = getFileName(uri) ?: "upload"
            updateTransient {
                copy(
                    isUploading = true,
                    uploadFilename = filename,
                    uploadProgress = null,
                    error = null,
                )
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                Timber.e("uploadFile: could not read bytes from URI: %s", uri)
                updateTransient {
                    copy(
                        isUploading = false,
                        uploadFilename = "",
                        uploadProgress = null,
                        error = "Could not read file",
                    )
                }
                return@launch
            }

            val fileId = UUID.randomUUID().toString()
            Timber.d("uploadFile: filename=%s, mimeType=%s, size=%d, fileId=%s", filename, mimeType, bytes.size, fileId)

            when (val result = fileRepository.uploadFile(
                bytes = bytes,
                filename = filename,
                type = mimeType,
                fileId = fileId,
                endpoint = "agents",
            )) {
                is Result.Success -> {
                    Timber.d("uploadFile: success -- serverFileId=%s", result.data.fileId)
                    _files.value = listOf(result.data) + _files.value
                    updateTransient {
                        copy(
                            isUploading = false,
                            uploadFilename = "",
                            uploadProgress = null,
                        )
                    }
                }
                is Result.Error -> {
                    Timber.e(result.exception, "uploadFile: server error -- %s", result.message)
                    updateTransient {
                        copy(
                            isUploading = false,
                            uploadFilename = "",
                            uploadProgress = null,
                            error = result.message ?: "Upload failed",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        updateTransient {
            copy(
                isUploading = false,
                uploadFilename = "",
                uploadProgress = null,
            )
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            val file = _files.value.find { it.fileId == fileId }
            val entry = DeleteFileEntry(
                fileId = fileId,
                filepath = file?.filepath ?: "",
            )
            when (val result = fileRepository.deleteFiles(listOf(entry))) {
                is Result.Success -> {
                    _files.value = _files.value.filter { it.fileId != fileId }
                }
                is Result.Error -> {
                    updateTransient {
                        copy(error = result.message ?: "Failed to delete file")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun setFilter(filter: FileTypeFilter) {
        _selectedFilter.value = filter
    }

    fun setSort(field: FileSortField, order: FileSortOrder) {
        _sortField.value = field
        _sortOrder.value = order
    }

    fun toggleViewMode() {
        updateTransient {
            copy(
                viewMode = when (viewMode) {
                    FileViewMode.LIST -> FileViewMode.GRID
                    FileViewMode.GRID -> FileViewMode.LIST
                },
            )
        }
    }

    // File preview

    fun openFilePreview(fileId: String) {
        val file = _files.value.find { it.fileId == fileId } ?: return
        updateTransient { copy(previewFile = file.toPreviewDisplayData()) }
    }

    fun closeFilePreview() {
        updateTransient { copy(previewFile = null) }
    }

    /**
     * Downloads the raw bytes of a file from the server.
     *
     * Uses [FileRepository.downloadFile] which calls `GET /api/files/download/:userId/:fileId`.
     * Returns null if the user ID is missing or the download fails.
     */
    suspend fun downloadFileBytes(fileId: String, userId: String?): ByteArray? {
        if (userId.isNullOrBlank()) {
            Timber.w("downloadFileBytes: userId is null/blank for fileId=%s", fileId)
            return null
        }
        return when (val result = fileRepository.downloadFile(userId, fileId)) {
            is Result.Success -> {
                Timber.d("downloadFileBytes: success, %d bytes for fileId=%s", result.data.size, fileId)
                result.data
            }
            is Result.Error -> {
                Timber.e(result.exception, "downloadFileBytes: error for fileId=%s: %s", fileId, result.message)
                null
            }
            is Result.Loading -> null
        }
    }

    // Multi-select methods

    fun enterEditMode() {
        updateTransient {
            copy(
                isSelectionMode = true,
                selectedFileIds = emptySet(),
            )
        }
    }

    fun enterSelectionMode(fileId: String) {
        updateTransient {
            copy(
                isSelectionMode = true,
                selectedFileIds = setOf(fileId),
            )
        }
    }

    fun exitSelectionMode() {
        updateTransient {
            copy(
                isSelectionMode = false,
                selectedFileIds = emptySet(),
            )
        }
    }

    fun toggleFileSelection(fileId: String) {
        val current = _transientState.value.selectedFileIds
        val updated = if (fileId in current) current - fileId else current + fileId
        if (updated.isEmpty()) {
            updateTransient {
                copy(
                    isSelectionMode = false,
                    selectedFileIds = emptySet(),
                )
            }
        } else {
            updateTransient { copy(selectedFileIds = updated) }
        }
    }

    fun selectAll() {
        val filtered = filterFiles(_files.value, _selectedFilter.value)
        val allIds = filtered.map { it.fileId }.toSet()
        updateTransient { copy(selectedFileIds = allIds) }
    }

    fun deleteSelected() {
        val selectedIds = _transientState.value.selectedFileIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val entries = selectedIds.mapNotNull { id ->
                val file = _files.value.find { it.fileId == id }
                file?.let { DeleteFileEntry(fileId = it.fileId, filepath = it.filepath) }
            }
            when (val result = fileRepository.deleteFiles(entries)) {
                is Result.Success -> {
                    _files.value = _files.value.filter { it.fileId !in selectedIds }
                    updateTransient {
                        copy(
                            isSelectionMode = false,
                            selectedFileIds = emptySet(),
                        )
                    }
                }
                is Result.Error -> {
                    updateTransient {
                        copy(error = result.message ?: "Failed to delete files")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        updateTransient { copy(error = null) }
    }

    // Private helpers

    private fun filterFiles(files: List<FileObject>, filter: FileTypeFilter): List<FileObject> =
        when (filter) {
            FileTypeFilter.ALL -> files
            FileTypeFilter.IMAGES -> files.filter { it.type.startsWith("image/") }
            FileTypeFilter.DOCUMENTS -> files.filter {
                it.type.startsWith("application/") || it.type.startsWith("text/")
            }
            FileTypeFilter.AUDIO -> files.filter { it.type.startsWith("audio/") }
            FileTypeFilter.VIDEO -> files.filter { it.type.startsWith("video/") }
        }

    private fun sortFiles(
        files: List<FileObject>,
        field: FileSortField,
        order: FileSortOrder,
    ): List<FileObject> {
        val sorted = when (field) {
            FileSortField.NAME -> files.sortedBy { it.filename.lowercase() }
            FileSortField.DATE -> files.sortedBy { it.createdAt ?: "" }
            FileSortField.SIZE -> files.sortedBy { it.bytes }
            FileSortField.TYPE -> files.sortedBy { it.type }
        }
        return if (order == FileSortOrder.DESCENDING) sorted.reversed() else sorted
    }

    /**
     * Builds the image preview URL for a file.
     *
     * The web frontend uses the `filepath` field directly (e.g. `/images/userId/file.webp`)
     * prepended with the server base URL. For images stored locally, this path is served
     * statically by the server. For non-local storage (S3, Firebase), `filepath` may already
     * be an absolute URL.
     *
     * As a fallback when `filepath` is blank, we use the download endpoint
     * (`/api/files/download/:userId/:fileId`) which streams the file with auth.
     */
    private fun FileObject.buildImagePreviewUrl(): String? {
        if (!type.startsWith("image/")) return null
        return buildFileUrl()
    }

    /**
     * Builds the download/preview URL for any file type.
     *
     * Uses the same logic as [buildImagePreviewUrl] but works for all MIME types.
     */
    private fun FileObject.buildFileUrl(): String {
        val baseUrl = serverDataStore.getBaseUrl().trimEnd('/')
        val url = if (filepath.startsWith("http://") || filepath.startsWith("https://")) {
            // Already an absolute URL (e.g. S3/CDN)
            filepath
        } else if (filepath.isNotBlank()) {
            // Relative path from server (e.g. /images/userId/file.webp)
            "$baseUrl$filepath"
        } else {
            // Fallback: use the download endpoint
            "$baseUrl/api/files/download/${user ?: ""}/$fileId"
        }
        Timber.d("File URL for %s (%s): %s", filename, fileId, url)
        return url
    }

    private fun FileObject.toDisplayData(): FileDisplayData {
        return FileDisplayData(
            fileId = fileId,
            filename = filename,
            type = type,
            formattedSize = Formatter.formatShortFileSize(context, bytes),
            createdAt = createdAt,
            previewUrl = buildImagePreviewUrl(),
        )
    }

    private fun FileObject.toPreviewDisplayData(): FilePreviewDisplayData {
        return FilePreviewDisplayData(
            fileId = fileId,
            filename = filename,
            type = type,
            formattedSize = Formatter.formatShortFileSize(context, bytes),
            createdAt = createdAt,
            source = source,
            previewUrl = buildImagePreviewUrl(),
            userId = user,
            downloadUrl = buildFileUrl(),
        )
    }

    private inline fun updateTransient(update: TransientState.() -> TransientState) {
        _transientState.value = _transientState.value.update()
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else {
                null
            }
        }
    }
}

private data class TransientState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadFilename: String = "",
    val uploadProgress: Float? = null,
    val error: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedFileIds: Set<String> = emptySet(),
    val previewFile: FilePreviewDisplayData? = null,
    val viewMode: FileViewMode = FileViewMode.LIST,
)
