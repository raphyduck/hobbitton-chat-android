package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.data.engine.TranscriptionFailure
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_forbidden
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_not_configured
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_rejected
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_signed_out
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_unavailable
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_unreachable
import org.jetbrains.compose.resources.StringResource

/**
 * The sentence for a failed transcription. One per cause, because the remedies differ: another
 * file, another moment, another sign-in, or nothing at all.
 */
internal fun TranscriptionFailure.message(): StringResource = when (this) {
    TranscriptionFailure.REJECTED -> Res.string.tasks_transcription_rejected
    TranscriptionFailure.PERMISSION -> Res.string.tasks_transcription_forbidden
    TranscriptionFailure.AUTHENTICATION -> Res.string.tasks_transcription_signed_out
    TranscriptionFailure.UNAVAILABLE -> Res.string.tasks_transcription_unavailable
    TranscriptionFailure.UNREACHABLE -> Res.string.tasks_transcription_unreachable
    TranscriptionFailure.NOT_CONFIGURED -> Res.string.tasks_transcription_not_configured
    TranscriptionFailure.UNKNOWN -> Res.string.tasks_transcription_failed
}
