package com.garfiec.librechat.feature.tasks.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.speech.SpeechToTextResponse
import com.garfiec.librechat.feature.tasks.MissionChatUiState
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ce qui attend dans le composeur : dictée, fichiers audio, photos.
 *
 * Whisper est remplacé par une lambda : ce qu'on vérifie ici, c'est où atterrissent les mots, pas
 * comment on les obtient.
 */
class ComposerStagingDelegateTest {

    private val audio = byteArrayOf(1, 2, 3)

    private fun entendu(texte: String): suspend (ByteArray, String) -> Result<SpeechToTextResponse> =
        { _, _ -> Result.Success(SpeechToTextResponse(texte)) }

    private val sourd: suspend (ByteArray, String) -> Result<SpeechToTextResponse> =
        { _, _ -> Result.Error(message = "whisper injoignable") }

    @Test
    fun `une dictee s'ajoute a ce qui est deja tape`() = runTest {
        val etat = MutableStateFlow(MissionChatUiState(input = "Fais le point "))
        val staging = ComposerStagingDelegate(etat, this, entendu("  sur la boîte mail "))

        staging.transcribeAudio(audio, "audio/mp4")
        assertTrue(etat.value.transcribing, "le composeur dit qu'il écoute avant que Whisper réponde")
        advanceUntilIdle()

        assertEquals("Fais le point sur la boîte mail", etat.value.input)
        assertFalse(etat.value.transcribing)
    }

    @Test
    fun `une transcription ratee garde le texte et le dit`() = runTest {
        val etat = MutableStateFlow(MissionChatUiState(input = "déjà là"))
        val staging = ComposerStagingDelegate(etat, this, sourd)

        staging.transcribeAudio(audio, "audio/mp4")
        advanceUntilIdle()

        assertEquals("déjà là", etat.value.input)
        assertTrue(etat.value.transcriptionFailed)
        assertFalse(etat.value.transcribing)

        staging.dismissTranscriptionError()
        assertFalse(etat.value.transcriptionFailed)
    }

    @Test
    fun `un second audio pendant que Whisper travaille est ignore`() = runTest {
        val etat = MutableStateFlow(MissionChatUiState())
        var appels = 0
        val staging = ComposerStagingDelegate(etat, this, { _, _ ->
            appels++
            Result.Success(SpeechToTextResponse("un"))
        })

        staging.transcribeAudio(audio, "audio/mp4")
        staging.attachAudio(audio, "audio/mpeg", "memo.mp3")
        advanceUntilIdle()

        assertEquals(1, appels)
        assertTrue(etat.value.audioNotes.isEmpty())
    }

    @Test
    fun `chaque fichier audio devient sa propre note, retirable`() = runTest {
        val etat = MutableStateFlow(MissionChatUiState())
        val staging = ComposerStagingDelegate(etat, this, entendu(" le texte entendu "))

        staging.attachAudio(audio, "audio/mpeg", "memo.mp3")
        advanceUntilIdle()
        staging.attachAudio(audio, "audio/mpeg", "suite.mp3")
        advanceUntilIdle()

        val notes = etat.value.audioNotes
        assertEquals(listOf("audio-0", "audio-1"), notes.map { it.id })
        assertEquals(listOf("memo.mp3", "suite.mp3"), notes.map { it.filename })
        assertEquals("le texte entendu", notes.first().text)
        // Le fichier va au fil, pas au composeur : le texte tapé n'est pas touché.
        assertEquals("", etat.value.input)

        staging.removeAudioNote("audio-0")
        assertEquals(listOf("suite.mp3"), etat.value.audioNotes.map { it.filename })
    }

    @Test
    fun `les photos s'empilent et se retirent par identifiant`() = runTest {
        val etat = MutableStateFlow(MissionChatUiState())
        val staging = ComposerStagingDelegate(etat, this, sourd)
        val une = StagedAttachment(id = "p1", mime = "image/jpeg", filename = "a.jpg", bytes = audio)
        val deux = StagedAttachment(id = "p2", mime = "image/jpeg", filename = "b.jpg", bytes = audio)

        staging.addAttachments(listOf(une))
        staging.addAttachments(listOf(deux))
        assertEquals(listOf("p1", "p2"), etat.value.attachments.map { it.id })

        staging.removeAttachment("p1")
        assertEquals(listOf("p2"), etat.value.attachments.map { it.id })
    }
}
