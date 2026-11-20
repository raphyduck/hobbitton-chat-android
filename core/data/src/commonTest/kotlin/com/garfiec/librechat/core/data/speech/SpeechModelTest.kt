package com.garfiec.librechat.core.data.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The dictation models' downloads (11/11/2026; three models and a choice since 20/11/2026): the
 * files pinned to one revision, the state read off their downloads, and the model in use.
 */
class SpeechModelTest {

    @Test
    fun `each model is pinned to its revision and weighs what Settings announces`() {
        val pinned = mapOf(
            SpeechModels.PARAKEET to ("ed5d745623472f97cfc2e6c2916ee248798fc5a8" to 671_220_638L),
            SpeechModels.CANARY_FLASH to ("9077164e0d3dd1d5353743e89ceaa1d3a770838c" to 207_813_900L),
            SpeechModels.CANARY_V2 to ("28a965808e76ab3182c10591338a0f7914d59c0d" to 1_386_236_724L),
        )
        assertEquals(SpeechModels.ALL, pinned.keys.toList())
        pinned.forEach { (model, expected) ->
            val (revision, bytes) = expected
            assertEquals(bytes, model.totalBytes, model.name)
            model.files.filter { "huggingface.co" in it.url }.forEach { file ->
                assertTrue("/resolve/$revision/" in file.url, file.url)
            }
            model.files.forEach { assertEquals(64, it.sha256.length, "${model.name} ${it.name}") }
        }
    }

    @Test
    fun `the models keep apart on the phone, and only the transducer has a joiner`() {
        assertEquals(SpeechModels.ALL.size, SpeechModels.ALL.map { it.id }.toSet().size)
        assertEquals(SpeechModels.ALL.size, SpeechModels.ALL.map { it.directory }.toSet().size)
        // Parakeet's files stay where the versions before 20/11/2026 downloaded them.
        assertEquals("speech/parakeet-tdt-0.6b-v3-int8", SpeechModels.PARAKEET.directory)
        SpeechModels.ALL.forEach { model ->
            assertEquals(model.kind == SpeechModelKind.TRANSDUCER, model.joiner != null, model.name)
        }
    }

    @Test
    fun `the dictation goes through the chosen model once installed, else the first installed`() {
        val absent = SpeechModelState.Absent
        val parakeet = ready(SpeechModels.PARAKEET)
        val flash = ready(SpeechModels.CANARY_FLASH)
        assertNull(activeSpeechModel(chosen = null, states = listOf(absent, absent, absent)))
        assertEquals(SpeechModels.PARAKEET, activeSpeechModel(null, listOf(parakeet, flash, absent))?.model)
        assertEquals(SpeechModels.CANARY_FLASH, activeSpeechModel("canary-180m-flash", listOf(parakeet, flash, absent))?.model)
        // The chosen one still downloads: the installed one serves meanwhile.
        val downloading = SpeechModelState.Downloading(done = 1, total = 2)
        assertEquals(SpeechModels.PARAKEET, activeSpeechModel("canary-1b-v2", listOf(parakeet, absent, downloading))?.model)
    }

    @Test
    fun `the library keeps the person's choice, and a choice read back never overrides it`() {
        val kept = mutableListOf<String>()
        val stores = listOf(FixedStore(SpeechModels.PARAKEET), FixedStore(SpeechModels.CANARY_FLASH))
        val library = SpeechModelLibrary(stores, remember = { kept += it })
        assertNull(library.active())
        stores.forEach { it.state.value = ready(it.model) }
        assertEquals(SpeechModels.PARAKEET, library.active()?.model)

        library.restore("canary-180m-flash")
        assertEquals(SpeechModels.CANARY_FLASH, library.active()?.model)
        assertEquals(emptyList(), kept)

        library.choose("parakeet-tdt-0.6b-v3")
        library.restore("canary-180m-flash")
        assertEquals(SpeechModels.PARAKEET, library.active()?.model)
        assertEquals(listOf("parakeet-tdt-0.6b-v3"), kept)
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

    private fun ready(model: SpeechModel) = SpeechModelState.Ready(
        SpeechModelPaths(
            model = model,
            encoder = "encoder",
            decoder = "decoder",
            joiner = model.joiner?.name,
            tokens = "tokens",
            vad = "vad",
        ),
    )

    private class FixedStore(override val model: SpeechModel) : SpeechModelStore {
        override val state = MutableStateFlow<SpeechModelState>(SpeechModelState.Absent)
        override val supported = true
        override fun download() = Unit
        override fun cancel() = Unit
        override fun delete() = Unit
    }
}
