package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.tasks.util.StagedAttachment

/** No picker on iOS yet: the Tasks tab's engine graph is Android-only (D-034). */
@Composable
internal actual fun rememberMissionAttachmentPicker(
    onPick: (List<StagedAttachment>) -> Unit,
): (() -> Unit)? = null
