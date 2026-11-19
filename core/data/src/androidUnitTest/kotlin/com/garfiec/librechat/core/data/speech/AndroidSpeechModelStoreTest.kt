package com.garfiec.librechat.core.data.speech

import android.app.DownloadManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** What the DownloadManager says, read as the model's download; and the files' hash. */
class AndroidSpeechModelStoreTest {

    @Test
    fun `the download manager's statuses read as the model's`() {
        assertEquals(FileDownload.DONE, fileDownload(DownloadManager.STATUS_SUCCESSFUL, 0))
        assertEquals(FileDownload.WAITING_FOR_WIFI, fileDownload(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI))
        assertEquals(FileDownload.PENDING, fileDownload(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_TO_RETRY))
        assertEquals(FileDownload.FAILED_STORAGE, fileDownload(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE))
        assertEquals(FileDownload.FAILED_NETWORK, fileDownload(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_HTTP_DATA_ERROR))
    }

    @Test
    fun `a file hashes to its sha-256 in lowercase hex`() {
        val file = File.createTempFile("speech", ".bin").apply { writeText("abc") }
        try {
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256(file))
        } finally {
            file.delete()
        }
    }

    /** E1 (19/11/2026): the poll is a second while the state is watched, 30 s otherwise. */
    @Test
    fun `a watched state is polled every second`() = runTest {
        awaitNextPoll(MutableStateFlow(1))
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun `an unwatched state is polled every 30 seconds`() = runTest {
        awaitNextPoll(MutableStateFlow(0))
        assertEquals(30_000L, testScheduler.currentTime)
    }

    @Test
    fun `a watcher arriving cuts the idle pause short`() = runTest {
        val watchers = MutableStateFlow(0)
        launch { delay(5_000); watchers.value = 1 }
        awaitNextPoll(watchers)
        assertEquals(5_000L, testScheduler.currentTime)
    }
}
