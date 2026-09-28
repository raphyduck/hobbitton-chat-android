package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.api.TranscriptionRefused
import kotlinx.coroutines.CancellationException

/**
 * The scheduler's `POST /transcription`, behind [AudioTranscriber].
 *
 * The transcription used to be LibreChat's speech route; LibreChat is gone (D-077) and the
 * scheduler now serves it, on the same host and with the same portal bearer as its MCP tools.
 *
 * @param configured whether a scheduler address is set — asked before anything is sent, so an
 *   install without one says so instead of failing as « unreachable ».
 * @param language the ISO 639-1 code to hint the transcription with, or null to let it detect the
 *   language itself. Read on every call: the device's language can change while the app runs.
 */
class SchedulerTranscriber(
    private val api: SchedulerApi,
    private val configured: suspend () -> Boolean,
    private val language: () -> String? = { null },
) : AudioTranscriber {

    override suspend fun transcribe(audio: ByteArray, mime: String, filename: String): TranscriptionOutcome {
        if (!configured()) return TranscriptionOutcome.Failed(TranscriptionFailure.NOT_CONFIGURED)
        return try {
            TranscriptionOutcome.Heard(api.transcribe(audio, mime, filename, language()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TranscriptionRefused) {
            TranscriptionOutcome.Failed(e.failure(), e.reason)
        } catch (e: Exception) {
            TranscriptionOutcome.Failed(e.engineFailureKind().asTranscriptionFailure())
        }
    }
}

/** A device language as the transcription takes it: a bare ISO 639-1 code, or nothing. */
fun isoLanguageOrNull(tag: String?): String? {
    val language = tag?.substringBefore('-')?.substringBefore('_')?.trim()?.lowercase() ?: return null
    // The JDK's historical codes, which Android still reports for these three languages.
    val current = LEGACY_LANGUAGE_CODES[language] ?: language
    return current.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }
}

private fun TranscriptionRefused.failure(): TranscriptionFailure = when (status) {
    400, 413, 415 -> TranscriptionFailure.REJECTED
    401 -> TranscriptionFailure.AUTHENTICATION
    403 -> TranscriptionFailure.PERMISSION
    // A scheduler that predates the route answers 404: to the person, it is simply not available.
    404, in 500..599 -> TranscriptionFailure.UNAVAILABLE
    else -> TranscriptionFailure.UNKNOWN
}

private fun EngineFailureKind.asTranscriptionFailure(): TranscriptionFailure = when (this) {
    EngineFailureKind.AUTHENTICATION -> TranscriptionFailure.AUTHENTICATION
    EngineFailureKind.PERMISSION -> TranscriptionFailure.PERMISSION
    EngineFailureKind.NOT_FOUND, EngineFailureKind.SERVER -> TranscriptionFailure.UNAVAILABLE
    EngineFailureKind.UNREACHABLE -> TranscriptionFailure.UNREACHABLE
    EngineFailureKind.UNKNOWN -> TranscriptionFailure.UNKNOWN
}

private val LEGACY_LANGUAGE_CODES = mapOf("iw" to "he", "in" to "id", "ji" to "yi")
