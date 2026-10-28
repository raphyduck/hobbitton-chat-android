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
