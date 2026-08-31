package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable

/** One audio file as picked: its bytes, what they claim to be, and the name the thread will show. */
class PickedAudio(val bytes: ByteArray, val mime: String, val filename: String)

/**
 * A launcher for the platform audio-file picker, or null where there is none to offer — the same
 * contract as [rememberMissionAttachmentPicker], for the same reason (D-034: Android only today).
 *
 * The bytes go to the server's Whisper, not to the mission's model: no model on the gateway hears
 * audio, so « deposit an audio » honestly means « transcribe it and send the words » — the caller
 * does that, this only picks.
 */
@Composable
internal expect fun rememberMissionAudioPicker(
    onPick: (PickedAudio) -> Unit,
): (() -> Unit)?
