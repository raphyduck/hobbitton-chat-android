package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel

/**
 * True when a photo is staged for a model that does not see images (24/10/2026, DeepSeek). The
 * composer then says so above the box: the platform's gateway will describe the photo in words
 * for that model, and a tap on the line offers another model for this conversation. An audio
 * note is not a photo; a file that is not an image is not either.
 */
fun imageStagedForBlindModel(model: EngineSelectableModel?, attachments: List<StagedAttachment>): Boolean =
    model?.seesImages == false && attachments.any { it.mime.startsWith(IMAGE_MIME_PREFIX) }

private const val IMAGE_MIME_PREFIX = "image/"
