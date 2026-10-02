package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.tasks.util.StagedAttachment

/**
 * A launcher for the platform photo and video picker, or **null where there is none to offer**.
 *
 * Null hides the attach button rather than showing one that does nothing. The engine graph is
 * Android-only today (D-034 — `tasksModule` is not in `sharedKoinModules`), so the iOS actual
 * returns null and this screen keeps compiling for both targets without pretending.
 *
 * The platform side owns reading and **downscaling** the bytes — see [StagedAttachment] for why
 * the bytes must arrive small.
 */
@Composable
internal expect fun rememberMissionAttachmentPicker(
    onPick: (List<StagedAttachment>) -> Unit,
): (() -> Unit)?

/** A launcher for the platform file picker — any document, any type — or null where there is none. */
@Composable
internal expect fun rememberMissionFilePicker(
    onPick: (List<StagedAttachment>) -> Unit,
): (() -> Unit)?
