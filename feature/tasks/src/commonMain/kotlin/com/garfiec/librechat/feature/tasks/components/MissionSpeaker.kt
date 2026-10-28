package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable

/**
 * The phone's voice (28/10/2026, voice mode step 4): reads a reply aloud with the device's own
 * speech synthesis, nothing leaving the phone.
 *
 * @param speaking a reading is under way; [stop] cuts it short.
 */
class MissionSpeaker(
    val speaking: Boolean,
    val speak: (String) -> Unit,
    val stop: () -> Unit,
)

/**
 * The device's speech synthesis, or null where the platform offers none (Android only today, as
 * [rememberMissionDictation]). [onDone] is called once a reading has ended, on the main thread,
 * whether it was heard to its end, cut short or refused by the engine.
 */
@Composable
internal expect fun rememberMissionSpeaker(onDone: () -> Unit): MissionSpeaker?
