package com.garfiec.librechat.feature.tasks.components

import android.content.Context
import android.net.Uri
import com.garfiec.librechat.feature.tasks.util.StagedAttachment

/**
 * The files another app shares into Eva (09/11/2026), staged exactly as the composer's picker
 * stages what the person picks: a photo shrunk to what a vision model reads, a video or a large
 * file kept for the deposit with its first frame, anything else read whole within the message's
 * ceiling. A URI that cannot be read stages nothing, as in the picker. Blocking reads: call it off
 * the main thread.
 */
fun stageSharedUris(context: Context, uris: List<String>): List<StagedAttachment> =
    uris.mapNotNull { raw ->
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
        context.stage(uri)
    }
