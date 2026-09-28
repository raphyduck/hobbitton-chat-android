package com.garfiec.librechat.core.data.engine

/**
 * Speech to text, as the composers need it: one audio in, the words or the reason there are none.
 *
 * An interface with one implementation ([SchedulerTranscriber]) so the screens can be tested
 * without a scheduler, and so that nothing above `:core:data` has to know which exceptions the
 * HTTP stack throws — the answer is already sorted into a [TranscriptionFailure].
 */
fun interface AudioTranscriber {
    suspend fun transcribe(audio: ByteArray, mime: String, filename: String): TranscriptionOutcome
}

/** What came back from a transcription. Never an exception: a failure is an answer like another. */
sealed interface TranscriptionOutcome {
    data class Heard(val text: String) : TranscriptionOutcome

    /**
     * @param reason the server's own words when it gave some (its `erreur`), untranslated — shown
     *   as a detail under a message the app does translate.
     */
    data class Failed(val failure: TranscriptionFailure, val reason: String? = null) : TranscriptionOutcome
}

/** Why a transcription failed, in the terms that change what the person should do next. */
enum class TranscriptionFailure {
    /** The server would not take this file: empty, too large, not audio. Another file may work. */
    REJECTED,

    /** Signed in, and not allowed to transcribe. Nothing to retry. */
    PERMISSION,

    /** The portal session has lapsed. Sign in again. */
    AUTHENTICATION,

    /** The route answered that transcription is unavailable (502 and kin). Later may work. */
    UNAVAILABLE,

    /** Nothing answered: no network, or the scheduler is down. */
    UNREACHABLE,

    /** No scheduler address is set, so there is nowhere to send the audio. */
    NOT_CONFIGURED,

    UNKNOWN,
}
