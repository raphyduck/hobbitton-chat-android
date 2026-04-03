package com.garfiec.librechat.feature.files.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.feature.files.FileDisplayData
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.platform.FileReader
import com.garfiec.librechat.feature.files.platform.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    private val fileReader: FileReader,
    private val serverDataStore: ServerDataStore,
) : ViewModel() {

    private val _files = MutableStateFlow<List<FileObject>>(emptyList())
    private val _selectedFilter = MutableStateFlow(FileTypeFilter.ALL)
    private val _sortField = MutableStateFlow(FileSortField.DATE)
    private val _sortOrder = MutableStateFlow(FileSortOrder.DESCENDING)

    private val _transientState = MutableStateFlow(TransientState())

    /** Cache display data by fileId to avoid re-running formatFileSize on every emission. */
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

    /**
     * Upload a file from a platform-specific file reference.
     * On Android this is a Uri; on iOS it will be an NSURL.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun uploadFile(fileRef: Any) {
        uploadJob = viewModelScope.launch {
            val filename = fileReader.getFileName(fileRef) ?: "upload"
            updateTransient {
                copy(
                    isUploading = true,
                    uploadFilename = filename,
                    uploadProgress = null,
                    error = null,
                )
            }
            val mimeType = fileReader.getMimeType(fileRef) ?: "application/octet-stream"
            val bytes = fileReader.readBytes(fileRef)
            if (bytes == null) {
                Logger.e { "uploadFile: could not read bytes from file reference" }
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

            val fileId = Uuid.random().toString()
            Logger.d { "uploadFile: filename=$filename, mimeType=$mimeType, size=${bytes.size}, fileId=$fileId" }

            when (val result = fileRepository.uploadFile(
                bytes = bytes,
                filename = filename,
                type = mimeType,
                fileId = fileId,
                endpoint = "agents",
            )) {
                is Result.Success -> {
                    Logger.d { "uploadFile: success -- serverFileId=${result.data.fileId}" }
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
                    Logger.e(result.exception) { "uploadFile: server error -- ${result.message}" }
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

    suspend fun downloadFileBytes(fileId: String, userId: String?): ByteArray? {
        if (userId.isNullOrBlank()) {
            Logger.w { "downloadFileBytes: userId is null/blank for fileId=$fileId" }
            return null
        }
        return when (val result = fileRepository.downloadFile(userId, fileId)) {
            is Result.Success -> {
                Logger.d { "downloadFileBytes: success, ${result.data.size} bytes for fileId=$fileId" }
                result.data
            }
            is Result.Error -> {
                Logger.e(result.exception) { "downloadFileBytes: error for fileId=$fileId: ${result.message}" }
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

    private fun FileObject.buildImagePreviewUrl(): String? {
        if (!type.startsWith("image/")) return null
        return buildFileUrl()
    }

    private fun FileObject.buildFileUrl(): String {
        val baseUrl = serverDataStore.getBaseUrl().trimEnd('/')
        val url = if (filepath.startsWith("http://") || filepath.startsWith("https://")) {
            filepath
        } else if (filepath.isNotBlank()) {
            "$baseUrl$filepath"
        } else {
            "$baseUrl/api/files/download/${user ?: ""}/$fileId"
        }
        Logger.d { "File URL for $filename ($fileId): $url" }
        return url
    }

    private fun FileObject.toDisplayData(): FileDisplayData {
        return FileDisplayData(
            fileId = fileId,
            filename = filename,
            type = type,
            formattedSize = formatFileSize(bytes),
            createdAt = createdAt,
            previewUrl = buildImagePreviewUrl(),
        )
    }

    private fun FileObject.toPreviewDisplayData(): FilePreviewDisplayData {
        return FilePreviewDisplayData(
            fileId = fileId,
            filename = filename,
            type = type,
            formattedSize = formatFileSize(bytes),
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
