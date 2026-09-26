package com.garfiec.librechat.feature.chat.components.artifact

import co.touchlab.kermit.Logger

/**
 * Per-WebView SVG sink. Immutable after construction — one receiver per
 * (cache, key) pair, allocated inside the AndroidView/UIKitView factory at the
 * mount site under a `key(mermaidKey) { ... }` block. Content/theme change
 * destroys the entire view slot, taking this receiver with it; the new slot
 * gets a fresh receiver bound to the new key.
 *
 * Per-key allocation eliminates the stale-capture race (a receiver can never
 * outlive the key it was constructed with) and the mid-render content-swap
 * race (the destroyed WebView's JS can no longer reach this receiver).
 *
 * What the page hands over is checked before it is cached ([isAcceptableRenderedSvg]): the
 * string is whatever script ran in that WebView, and the cache entry is drawn natively in place
 * of the diagram on every later recompose (review C3, 26/09/2026).
 */
internal class MermaidBridgeReceiver(
    private val cache: MermaidRenderCache,
    private val key: String,
) {
    fun onSvg(svg: String) {
        if (!isAcceptableRenderedSvg(svg)) {
            Logger.w { "MermaidBridge: refused a rendered diagram that is not a plain SVG" }
            return
        }
        cache.put(key, svg)
    }
}

/** Larger than any diagram mermaid draws; a bound so a page cannot fill the cache with one entry. */
internal const val MAX_RENDERED_SVG_CHARS = 2_000_000

private val EVENT_HANDLER_ATTRIBUTE = Regex("""[\s"'/]on[a-z]+\s*=""")

/**
 * Whether a string the mermaid page reported may be cached and drawn natively: it must be an
 * `<svg>` document of bounded size carrying no script element, no event-handler attribute and no
 * `javascript:` reference. The native decoder executes none of those anyway; the check keeps
 * the cache holding diagrams rather than arbitrary markup.
 */
internal fun isAcceptableRenderedSvg(svg: String): Boolean {
    if (svg.length > MAX_RENDERED_SVG_CHARS) return false
    if (!svg.trimStart().startsWith("<svg", ignoreCase = true)) return false
    val lower = svg.lowercase()
    if ("<script" in lower || "javascript:" in lower) return false
    return !EVENT_HANDLER_ATTRIBUTE.containsMatchIn(lower)
}
