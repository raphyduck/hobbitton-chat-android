package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig

/**
 * The images a message names in markdown, `![description](https://…)`, drawn in the message
 * (23/10/2026, Raphaël: two tickets' QR codes came as links to copy and open, minutes before
 * the concert). Loaded by the app's Coil loader, the one the attached photos already use; the
 * renderer sizes them from their own dimensions once they have arrived. Since 04/11/2026 the
 * message draws its images itself (`splitOutImages`, `MessageImage`); the renderer only meets
 * one the split did not take, a reference-style image for instance.
 */
internal object MarkdownImages : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData {
        // Decoded no larger than [MAX_EDGE_PX] a side (26/10/2026 evening): a photo of 12 Mpx at its
        // own size is 48 MB of bitmap for a bubble a few hundred pixels wide. The renderer scales
        // it to the bubble either way.
        val request = ImageRequest.Builder(LocalPlatformContext.current)
            .data(link)
            .size(MAX_EDGE_PX)
            .build()
        return ImageData(painter = rememberAsyncImagePainter(request))
    }

    internal const val MAX_EDGE_PX = 1_600

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is AsyncImagePainter) {
            val state by painter.state.collectAsState()
            state.painter?.intrinsicSize?.let { size = it }
        }
        return size
    }

    /**
     * The room an image takes in its line of text (04/11/2026, Raphaël's recording: a product
     * photo drawn as a thin white band, a tall hole under it). Every image a message names is
     * inline for the renderer, and its own rule caps the image at the HEIGHT of the text's parent
     * as measured at that instant: a one-line paragraph allows a band a line high, the band makes
     * the paragraph taller, the next measure allows more, and the layout chases itself. Here the
     * width is the only bound: the image's own width or the container's, whichever is smaller, the
     * height following the image's ratio; size still unknown, a square of 200 dp at most.
     */
    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((link: String, Size) -> Unit)?,
    ): PlaceholderConfig {
        val side = ImageTransformer.DEFAULT_IMAGE_SIDE_LENGTH_DP
        val containerWidthDp = with(density) { containerSize.width.toDp().value }
            .takeIf { !containerSize.isUnspecified && it > 0f }
        if (imageSize.isUnspecified || imageSize.width <= 0f || imageSize.height <= 0f) {
            val square = minOf(side, containerWidthDp ?: side)
            return PlaceholderConfig(Size(square, square))
        }
        val imageWidthDp = with(density) { imageSize.width.toDp().value }
        val imageHeightDp = with(density) { imageSize.height.toDp().value }
        val width = minOf(imageWidthDp, containerWidthDp ?: imageWidthDp)
        return PlaceholderConfig(Size(width, imageHeightDp * width / imageWidthDp))
    }
}
