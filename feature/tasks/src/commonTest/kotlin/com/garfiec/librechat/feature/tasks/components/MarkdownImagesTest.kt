package com.garfiec.librechat.feature.tasks.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.mikepenz.markdown.model.ImageWidth
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownImagesTest {

    private val density = Density(2f)

    private fun placeholder(container: Size, image: Size): Size = MarkdownImages
        .placeholderConfig("https://x/y.png", density, container, ImageWidth.IMAGE_WIDTH, image, null)
        .size

    @Test
    fun `a wide photo in a one-line paragraph fills the width and keeps its ratio`() {
        // 1 600 × 1 200 px decoded, a 720 px wide container that is 60 px high at that instant.
        val size = placeholder(container = Size(720f, 60f), image = Size(1_600f, 1_200f))
        assertEquals(360f, size.width)
        assertEquals(270f, size.height)
    }

    @Test
    fun `a small image keeps its own size`() {
        val size = placeholder(container = Size(720f, 40f), image = Size(200f, 100f))
        assertEquals(100f, size.width)
        assertEquals(50f, size.height)
    }

    @Test
    fun `an image whose size is still unknown gets a square no wider than the container`() {
        assertEquals(Size(200f, 200f), placeholder(container = Size(720f, 60f), image = Size.Unspecified))
        assertEquals(Size(150f, 150f), placeholder(container = Size(300f, 60f), image = Size.Unspecified))
        assertEquals(Size(200f, 200f), placeholder(container = Size.Unspecified, image = Size.Unspecified))
    }

    @Test
    fun `a container not placed yet does not bound the image`() {
        val size = placeholder(container = Size.Unspecified, image = Size(400f, 400f))
        assertEquals(Size(200f, 200f), size)
    }
}
