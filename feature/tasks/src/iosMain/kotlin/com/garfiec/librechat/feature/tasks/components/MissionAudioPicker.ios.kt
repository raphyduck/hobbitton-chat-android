package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable

/** No picker on iOS yet: the Tasks tab's engine graph is Android-only (D-034). */
@Composable
internal actual fun rememberMissionAudioPicker(
    onPick: (PickedAudio) -> Unit,
): (() -> Unit)? = null
