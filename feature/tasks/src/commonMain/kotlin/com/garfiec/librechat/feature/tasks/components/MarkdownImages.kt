package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * The images a message names in markdown, `![description](https://…)`, drawn in the message
 * (23/10/2026, Raphaël: two tickets' QR codes came as links to copy and open, minutes before
 * the concert). Loaded by the app's Coil loader, the one the attached photos already use; the
 * renderer sizes them from their own dimensions once they have arrived.
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

    private const val MAX_EDGE_PX = 1_600

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is AsyncImagePainter) {
            val state by painter.state.collectAsState()
            state.painter?.intrinsicSize?.let { size = it }
        }
        return size
    }
}
