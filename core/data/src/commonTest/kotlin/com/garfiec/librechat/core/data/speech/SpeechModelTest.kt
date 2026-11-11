package com.garfiec.librechat.core.data.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dictation model's download (11/11/2026): the files pinned to one revision, and the state
 * read off their downloads.
 */
class SpeechModelTest {

    @Test
    fun `the model is pinned to its revision and weighs what Settings announces`() {
        assertEquals(671_220_638, ParakeetModel.TOTAL_BYTES)
        ParakeetModel.FILES.filter { "huggingface.co" in it.url }.forEach { file ->
            assertTrue("/resolve/ed5d745623472f97cfc2e6c2916ee248798fc5a8/" in file.url, file.url)
        }
        ParakeetModel.FILES.forEach { assertEquals(64, it.sha256.length, it.name) }
    }

    @Test
    fun `a running download counts its bytes, and waits for wifi only when nothing runs`() {
        val running = downloadState(listOf(FileDownload.RUNNING to 100L, FileDownload.WAITING_FOR_WIFI to 0L), 1_000)
        assertEquals(SpeechModelState.Downloading(done = 100, total = 1_000, waitingForWifi = false), running)
        val waiting = downloadState(listOf(FileDownload.DONE to 10L, FileDownload.WAITING_FOR_WIFI to 5L), 1_000)
        assertEquals(SpeechModelState.Downloading(done = 15, total = 1_000, waitingForWifi = true), waiting)
        assertEquals(0.015f, (waiting as SpeechModelState.Downloading).fraction)
    }

    @Test
    fun `every file done is the check, a failure wins, a forgotten download is a network failure`() {
        assertEquals(SpeechModelState.Verifying, downloadState(listOf(FileDownload.DONE to 1L, FileDownload.DONE to 2L), 3))
        assertEquals(
            SpeechModelState.Failed(SpeechModelFailure.STORAGE),
            downloadState(listOf(FileDownload.FAILED_NETWORK to 0L, FileDownload.FAILED_STORAGE to 0L), 3),
        )
        assertEquals(
            SpeechModelState.Failed(SpeechModelFailure.NETWORK),
            downloadState(listOf(FileDownload.DONE to 1L, FileDownload.MISSING to 0L), 3),
        )
    }
}
