package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ModelPrices

/**
 * True when a photo is staged for a model that does not read images (24/10/2026, DeepSeek). The
 * composer then says so above the box: the platform's gateway will describe the photo in words
 * for that model, and a tap on the line offers another model for this conversation.
 *
 * Who says a model is blind is the gateway, through the scheduler's price table
 * ([ModelPrices.byModel], `voit_images`), not the engine: the engine is told DeepSeek takes
 * images, precisely so that it forwards them to the gateway instead of refusing them. A model the
 * table does not know, or says nothing about, is taken as seeing: a warning on every model would be
 * a warning on none. An audio note is not a photo; a file that is not an image is not either.
 */
fun imageStagedForBlindModel(
    model: EngineSelectableModel?,
    prices: ModelPrices,
    attachments: List<StagedAttachment>,
): Boolean {
    val blind = model != null && prices.byModel[model.modelId.lowercase()]?.seesImages == false
    return blind && attachments.any { it.mime.startsWith(IMAGE_MIME_PREFIX) }
}

private const val IMAGE_MIME_PREFIX = "image/"
