package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable

/** The dictation control's face: whether the mic is live, and the tap that starts or stops it. */
class MissionDictation(val recording: Boolean, val toggle: () -> Unit)

/**
 * A tap-to-talk dictation for the mission composer, or null where the platform offers none
 * (D-034: Android only today — same contract as [rememberMissionAttachmentPicker]).
 *
 * First tap starts recording, asking for the microphone permission if needed; the second stops it
 * and hands the recording over. The caller sends it to the server's Whisper and puts the words in
 * the **composer** — the dictation contract: the speaker reads what Whisper heard and fixes it
 * before it becomes an instruction. A deposited *file* goes to the thread instead; the two must
 * not swap places (demanded 31/08/2026).
 */
@Composable
internal expect fun rememberMissionDictation(
    onCapture: (PickedAudio) -> Unit,
): MissionDictation?
