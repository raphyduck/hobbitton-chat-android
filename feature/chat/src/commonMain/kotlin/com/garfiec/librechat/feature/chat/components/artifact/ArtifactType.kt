package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Classification of an artifact MIME type into the renderer/icon families the
 * UI knows about. Centralises the substring-matching ladder that used to live
 * in `ArtifactButton`, `ArtifactPanel.isPreviewableType`, `ArtifactWebContent`,
 * and `InlineArtifactPrefs.shouldRenderInline` — all four sites now classify
 * via [from] and switch on the resulting enum.
 *
 * Matching order matters: `application/vnd.code-html` must be classified as
 * HTML, not CODE. The [from] cases are ordered so the most-specific tokens
 * win first.
 */
enum class ArtifactType {
    MERMAID,
    REACT,
    SVG,
    MARKDOWN,
    HTML,
    PLAIN,
    CODE;

    companion object {
        fun from(mime: String): ArtifactType = when {
            mime.contains("mermaid") -> MERMAID
            mime.contains("react") -> REACT
            mime.contains("svg") -> SVG
            mime.contains("markdown") || mime == "text/md" -> MARKDOWN
            mime.contains("html") -> HTML
            mime == "text/plain" -> PLAIN
            else -> CODE
        }
    }
}
