package com.garfiec.librechat.feature.tasks.util

/**
 * An audio file already transcribed, waiting to leave with the next message.
 *
 * The words are the payload: no model on the gateway hears audio, so what actually travels — and
 * what the thread shows — is the transcription, quoted under the file's name. The bytes are not
 * kept: once Whisper has answered, the file has nothing left to say.
 */
class AudioNote(
    val id: String,
    val filename: String,
    val text: String,
)

/**
 * The message as it leaves: the typed words first, then each transcription as a quoted block
 * naming its file.
 *
 * The quote is what makes the thread legible — « this part came from an audio, and here is which
 * one » — and for the model the words are simply part of the message, which is the only form it
 * can read. Demanded on 31/08/2026: a deposited audio must land in the **thread**, not in the
 * composer; the composer is the dictation's contract, not the file's.
 */
fun outgoingMessageText(input: String, notes: List<AudioNote>): String =
    (listOf(input.trim()) + notes.map { it.quotedBlock() })
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

/** `> 🎙️ name` then the transcription, every line quoted so markdown keeps the block together. */
private fun AudioNote.quotedBlock(): String =
    (listOf("🎙️ $filename") + text.trim().lines().filter { it.isNotBlank() })
        .joinToString("\n") { "> $it" }
