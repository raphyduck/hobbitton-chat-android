package com.garfiec.librechat.core.ui.markdown

/**
 * Which images a message draws on its own: any `https:` or `data:` image (27/10/2026, Raphaël:
 * « il faut pouvoir afficher des images dans les sessions »; from 26/10 to 27/10 only the
 * platform's hosts drew, and the product photos a search brought back all showed as links).
 * A cleartext or odd-scheme image stays a link the person opens on purpose.
 *
 * Known cost, accepted with Raphaël's rule of 27/10 (open, guards rather than restrictions): the
 * agent reads third parties, and a trapped content can make the model write an image URL that
 * carries what it knows out to a host of its choosing; the phone fetches it at the first draw.
 */
private val MARKDOWN_IMAGE_REGEX = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(\s+"[^"]*")?\s*\)""")

/** [markdown] with every image that does not load on its own turned into a link, `[alt (image)](url)`. */
fun insecureImagesAsLinks(markdown: String): String {
    if ('!' !in markdown) return markdown
    return MARKDOWN_IMAGE_REGEX.replace(markdown) { match ->
        val alt = match.groupValues[1].trim()
        val url = match.groupValues[2]
        if (imageLoadsOnItsOwn(url)) match.value else "[${alt.ifEmpty { "image" }} (image)]($url)"
    }
}

/** True for an `https:` image and a `data:` image. */
fun imageLoadsOnItsOwn(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("data:", ignoreCase = true)
}

/** A piece of a message's prose once its images are taken out: markdown, or one image. */
sealed interface ProsePart {
    data class Text(val markdown: String) : ProsePart
    data class Image(val url: String, val description: String) : ProsePart
}

// A line that is only a list marker, left behind when the item's content was an image.
private val DANGLING_BULLET = Regex("""(?:^|\n)[ \t]*(?:[-*+]|\d+[.)])[ \t]*$""")

/**
 * [markdown] cut around every image that loads on its own: the text between them stays markdown,
 * each image is a part of its own, drawn by the message itself and never by the markdown
 * renderer (04/11/2026, Raphaël's two recordings: a product photo drawn as a band one line high,
 * a tall hole under it; the renderer sizes an image inside its line of text from the paragraph
 * around it, and the layout chases itself). An image that does not load on its own was already
 * made a link by [insecureImagesAsLinks] and stays in the text.
 */
fun splitOutImages(markdown: String): List<ProsePart> {
    if ('!' !in markdown) return listOf(ProsePart.Text(markdown))
    val parts = mutableListOf<ProsePart>()
    var cursor = 0
    for (match in MARKDOWN_IMAGE_REGEX.findAll(markdown)) {
        val url = match.groupValues[2].trim()
        if (!imageLoadsOnItsOwn(url)) continue
        val before = markdown.substring(cursor, match.range.first).trim().replace(DANGLING_BULLET, "")
        if (before.isNotBlank()) parts += ProsePart.Text(before)
        parts += ProsePart.Image(url, match.groupValues[1].trim())
        cursor = match.range.last + 1
    }
    val after = markdown.substring(cursor).trimStart()
    if (after.isNotBlank()) parts += ProsePart.Text(after)
    return parts.ifEmpty { listOf(ProsePart.Text(markdown)) }
}
