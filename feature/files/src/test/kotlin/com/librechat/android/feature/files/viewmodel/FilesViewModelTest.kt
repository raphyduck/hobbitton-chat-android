package com.librechat.android.feature.files.viewmodel

import android.app.Application
import android.text.format.Formatter
import com.google.common.truth.Truth.assertThat
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.repository.FileRepository
import com.librechat.android.core.model.FileObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fileRepository = mockk<FileRepository>(relaxed = true)
    private val context = mockk<Application>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)

    private lateinit var viewModel: FilesViewModel

    private val testFiles = listOf(
        FileObject(
            fileId = "file-1",
            filename = "document.pdf",
            filepath = "/files/user1/document.pdf",
            type = "application/pdf",
            bytes = 1024L,
            createdAt = "2026-02-19T10:00:00.000Z",
        ),
        FileObject(
            fileId = "file-2",
            filename = "photo.jpg",
            filepath = "/images/user1/photo.jpg",
            type = "image/jpeg",
            bytes = 2048L,
            createdAt = "2026-02-18T10:00:00.000Z",
        ),
        FileObject(
            fileId = "file-3",
            filename = "song.mp3",
            filepath = "/audio/user1/song.mp3",
            type = "audio/mpeg",
            bytes = 4096L,
            createdAt = "2026-02-17T10:00:00.000Z",
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Formatter::class)
        every { Formatter.formatShortFileSize(any(), any()) } returns "1 KB"
        coEvery { fileRepository.getFiles() } returns Result.Success(testFiles)
        every { serverDataStore.getBaseUrl() } returns "https://chat.example.com"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Formatter::class)
    }

    private fun createViewModel() = FilesViewModel(
        fileRepository = fileRepository,
        context = context,
        serverDataStore = serverDataStore,
    )

    @Test
    fun `initial state loads files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.displayFiles).hasSize(3)
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasFiles).isTrue()
    }

    @Test
    fun `loadFiles error shows error message`() = runTest {
        coEvery { fileRepository.getFiles() } returns Result.Error(message = "Network error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Network error")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `empty file list shows hasFiles false`() = runTest {
        coEvery { fileRepository.getFiles() } returns Result.Success(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasFiles).isFalse()
        assertThat(viewModel.uiState.value.displayFiles).isEmpty()
    }

    @Test
    fun `setFilter with IMAGES shows only image files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(FileTypeFilter.IMAGES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.displayFiles).hasSize(1)
        assertThat(state.displayFiles[0].fileId).isEqualTo("file-2")
        assertThat(state.selectedFilter).isEqualTo(FileTypeFilter.IMAGES)
    }

    @Test
    fun `setFilter with DOCUMENTS shows only document files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(FileTypeFilter.DOCUMENTS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.displayFiles).hasSize(1)
        assertThat(state.displayFiles[0].fileId).isEqualTo("file-1")
    }

    @Test
    fun `setFilter with AUDIO shows only audio files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(FileTypeFilter.AUDIO)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.displayFiles).hasSize(1)
        assertThat(viewModel.uiState.value.displayFiles[0].fileId).isEqualTo("file-3")
    }

    @Test
    fun `setFilter with ALL shows all files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(FileTypeFilter.IMAGES)
        advanceUntilIdle()
        viewModel.setFilter(FileTypeFilter.ALL)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.displayFiles).hasSize(3)
    }

    @Test
    fun `deleteFile removes file from list on success`() = runTest {
        coEvery { fileRepository.deleteFiles(any()) } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteFile("file-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.displayFiles).hasSize(2)
        assertThat(state.displayFiles.any { it.fileId == "file-1" }).isFalse()
    }

    @Test
    fun `deleteFile error shows error message`() = runTest {
        coEvery { fileRepository.deleteFiles(any()) } returns
            Result.Error(message = "Delete failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteFile("file-1")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Delete failed")
        assertThat(viewModel.uiState.value.displayFiles).hasSize(3)
    }

    @Test
    fun `refresh reloads files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isRefreshing).isFalse()
        coVerify(exactly = 2) { fileRepository.getFiles() }
    }

    @Test
    fun `dismissError clears error state`() = runTest {
        coEvery { fileRepository.getFiles() } returns Result.Error(message = "Error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.dismissError()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `enterSelectionMode enables selection with initial file`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterSelectionMode("file-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isTrue()
        assertThat(state.selectedFileIds).containsExactly("file-1")
    }

    @Test
    fun `toggleFileSelection adds and removes files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterSelectionMode("file-1")
        advanceUntilIdle()

        viewModel.toggleFileSelection("file-2")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedFileIds).containsExactly("file-1", "file-2")

        viewModel.toggleFileSelection("file-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedFileIds).containsExactly("file-2")
    }

    @Test
    fun `toggleFileSelection exits selection mode when last file deselected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterSelectionMode("file-1")
        advanceUntilIdle()

        viewModel.toggleFileSelection("file-1")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(viewModel.uiState.value.selectedFileIds).isEmpty()
    }

    @Test
    fun `exitSelectionMode clears selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterSelectionMode("file-1")
        advanceUntilIdle()

        viewModel.exitSelectionMode()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(viewModel.uiState.value.selectedFileIds).isEmpty()
    }

    @Test
    fun `deleteSelected removes selected files on success`() = runTest {
        coEvery { fileRepository.deleteFiles(any()) } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterSelectionMode("file-1")
        advanceUntilIdle()
        viewModel.toggleFileSelection("file-2")
        advanceUntilIdle()

        viewModel.deleteSelected()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.displayFiles).hasSize(1)
        assertThat(state.displayFiles[0].fileId).isEqualTo("file-3")
        assertThat(state.isSelectionMode).isFalse()
    }

    @Test
    fun `toggleViewMode switches between list and grid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.viewMode).isEqualTo(FileViewMode.LIST)

        viewModel.toggleViewMode()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.viewMode).isEqualTo(FileViewMode.GRID)

        viewModel.toggleViewMode()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.viewMode).isEqualTo(FileViewMode.LIST)
    }

    @Test
    fun `setSort updates sort field and order`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSort(FileSortField.NAME, FileSortOrder.ASCENDING)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.sortField).isEqualTo(FileSortField.NAME)
        assertThat(state.sortOrder).isEqualTo(FileSortOrder.ASCENDING)
    }

    @Test
    fun `downloadFileBytes returns null for blank userId`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.downloadFileBytes("file-1", null)
        assertThat(result).isNull()
    }

    @Test
    fun `downloadFileBytes returns bytes on success`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { fileRepository.downloadFile("user-1", "file-1") } returns Result.Success(bytes)

        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.downloadFileBytes("file-1", "user-1")
        assertThat(result).isEqualTo(bytes)
    }

    @Test
    fun `downloadFileBytes returns null on error`() = runTest {
        coEvery { fileRepository.downloadFile("user-1", "file-1") } returns
            Result.Error(message = "Not found")

        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.downloadFileBytes("file-1", "user-1")
        assertThat(result).isNull()
    }

    @Test
    fun `selectAll selects all visible files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enterEditMode()
        advanceUntilIdle()
        viewModel.selectAll()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedFileIds)
            .containsExactly("file-1", "file-2", "file-3")
    }
}
