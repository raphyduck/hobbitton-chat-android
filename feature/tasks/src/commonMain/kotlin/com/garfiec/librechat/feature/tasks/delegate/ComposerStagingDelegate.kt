package com.garfiec.librechat.feature.tasks.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.speech.SpeechToTextResponse
import com.garfiec.librechat.feature.tasks.MissionChatUiState
import com.garfiec.librechat.feature.tasks.util.AudioNote
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * What waits in a mission's composer for the next message: photos, transcribed audio files, and
 * the dictation that lands in the text box.
 *
 * Carved out of `MissionChatViewModel` (D-076), on the chat's delegate model: the ViewModel keeps
 * the conversation — history, feed, send — and this keeps the staging, writing into the same
 * [state]. What is staged here leaves with `MissionChatViewModel.send`, which empties it.
 *
 * Whisper takes one audio at a time here, hence the single `transcribing` flag and the early return
 * while it is up: a second recording would otherwise race the first into the box.
 */
internal class ComposerStagingDelegate(
    private val state: MutableStateFlow<MissionChatUiState>,
    private val scope: CoroutineScope,
    private val transcribe: suspend (bytes: ByteArray, mime: String) -> Result<SpeechToTextResponse>,
    /** Where the transcription runs: the IO dispatcher from the ViewModel. */
    private val context: CoroutineContext = EmptyCoroutineContext,
) {

    private var audioNoteSeq = 0

    /**
     * The dictation: a voice recording becomes words in the **composer**.
     *
     * The speaker sees what Whisper heard and can fix it before it becomes an instruction — the
     * chat's dictation contract, applied here. Demanded as such on 31/08/2026: the composer is
     * where a *dictation* lands; a deposited file goes to the thread instead ([attachAudio]).
     */
    fun transcribeAudio(bytes: ByteArray, mime: String) {
        if (state.value.transcribing) return
        state.update { it.copy(transcribing = true, transcriptionFailed = false) }
        scope.launch {
            val transcribed = withContext(context) { transcribe(bytes, mime) }
            state.update { current ->
                when (transcribed) {
                    is Result.Success -> current.copy(
                        transcribing = false,
                        input = listOf(current.input.trimEnd(), transcribed.data.text.trim())
                            .filter { it.isNotEmpty() }
                            .joinToString(" "),
                    )
                    else -> current.copy(transcribing = false, transcriptionFailed = true)
                }
            }
        }
    }

    /**
     * A deposited audio file becomes a quoted transcription in the **thread**.
     *
     * Transcribed on pick, not on send: a failure surfaces while the person is still here to see
     * it, and the send itself stays instant. What is staged is the *words* ([AudioNote]) — the
     * bytes are dropped once Whisper has answered, because no model on the gateway could read
     * them and the transcript should not carry megabytes nobody can open.
     */
    fun attachAudio(bytes: ByteArray, mime: String, filename: String) {
        if (state.value.transcribing) return
        state.update { it.copy(transcribing = true, transcriptionFailed = false) }
        // Minted outside the update: an `update` block is a CAS loop and may re-run.
        val id = "audio-${audioNoteSeq++}"
        scope.launch {
            val transcribed = withContext(context) { transcribe(bytes, mime) }
            state.update { current ->
                when (transcribed) {
                    is Result.Success -> current.copy(
                        transcribing = false,
                        audioNotes = current.audioNotes +
                            AudioNote(id, filename, transcribed.data.text.trim()),
                    )
                    else -> current.copy(transcribing = false, transcriptionFailed = true)
                }
            }
        }
    }

    fun removeAudioNote(id: String) {
        state.update { current -> current.copy(audioNotes = current.audioNotes.filterNot { it.id == id }) }
    }

    fun dismissTranscriptionError() {
        state.update { it.copy(transcriptionFailed = false) }
    }

    fun addAttachments(staged: List<StagedAttachment>) {
        state.update { it.copy(attachments = it.attachments + staged) }
    }

    fun removeAttachment(id: String) {
        state.update { current -> current.copy(attachments = current.attachments.filterNot { it.id == id }) }
    }
}
