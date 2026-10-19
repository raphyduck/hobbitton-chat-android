package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome

/**
 * The dictation control's face: whether the mic is live, and the tap that starts or stops it.
 *
 * @param listening the mic is live on the phone's own recognition (rather than recording for the
 *   scheduler), whose words come as they are spoken: [heard] holds the stretch still being heard,
 *   before it lands in the composer.
 */
class MissionDictation(
    val recording: Boolean,
    val toggle: () -> Unit,
    val listening: Boolean = false,
    val heard: String = "",
)

/**
 * A tap-to-talk dictation for the mission composer, or null where the platform offers none
 * (D-034: Android only today — same contract as [rememberMissionAttachmentPicker]).
 *
 * First tap starts it, asking for the microphone permission if needed; the second stops it. With
 * [onDevice] (Settings, on by default since 19/10/2026) the phone's own recognition hears it and
 * hands the words to [onHear] one stretch of speech at a time, nothing leaving the device; the
 * platform falls back to the recording where the phone has no recognition of its own. Otherwise
 * the second tap hands the recording to [onCapture], for the scheduler's transcription.
 *
 * Either way the words land in the **composer** — the dictation contract: the speaker reads what
 * was heard and fixes it before it becomes an instruction. A deposited *file* goes to the thread
 * instead; the two must not swap places (demanded 31/08/2026).
 */
@Composable
internal expect fun rememberMissionDictation(
    onDevice: Boolean,
    onCapture: (PickedAudio) -> Unit,
    onHear: (String) -> Unit,
    onError: (TranscriptionOutcome.Failed) -> Unit,
): MissionDictation?
