package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Builds an HTML page that renders Markdown content using marked.js (GFM)
 * with highlight.js for code syntax highlighting.
 *
 * The Markdown reaches the page as a base64 literal ([ArtifactWebContent.base64Literal]), never
 * spliced into a template string: an escape that only covered backslashes, backticks and `$`
 * left a closing script tag inside the content free to end the `<script>` element at the HTML
 * level (review C3, 26/09/2026). The HTML marked produces is passed through DOMPurify before it
 * touches the DOM, so raw HTML in the Markdown renders as inert markup rather than as script —
 * a "Markdown" artifact reads as text to the user and must behave like one.
 */
object MarkdownWebContent {

    /** jsdelivr for the pinned libraries; any `https:` host for the images the Markdown embeds. */
    val RESOURCE_POLICY = WebResourcePolicy(setOf(CdnAssets.JSDELIVR_HOST), anyHttps = true)

    private const val CSP = "default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; " +
        "style-src 'unsafe-inline' https://cdn.jsdelivr.net; img-src data: blob: https:; " +
        "${ArtifactWebContent.CSP_NO_FRAMES} form-action 'none'; base-uri 'none'; object-src 'none';"

    fun buildHtml(markdownContent: String, isDarkTheme: Boolean, inline: Boolean = false): String {
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
        val codeBg = if (isDarkTheme) "#2B2930" else "#F3EDF7"
        val borderColor = if (isDarkTheme) "#48464C" else "#CAC4D0"
        val linkColor = if (isDarkTheme) "#D0BCFF" else "#6750A4"
        val hlStyle = if (isDarkTheme) CdnAssets.HLJS_STYLE_DARK else CdnAssets.HLJS_STYLE_LIGHT
        val bodyPadding = if (inline) "8px" else "16px"
        val contentLiteral = ArtifactWebContent.base64Literal(markdownContent)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta http-equiv="Content-Security-Policy" content="$CSP">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                ${hlStyle.stylesheetTag()}
                <style>
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body {
                        margin: 0;
                        padding: $bodyPadding;
                        background: $bgColor;
                        color: $fgColor;
                        font-family: -apple-system, system-ui, sans-serif;
                        font-size: 15px;
                        line-height: 1.6;
                        word-wrap: break-word;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        margin-top: 1.2em;
                        margin-bottom: 0.5em;
                        font-weight: 600;
                    }
                    h1 { font-size: 1.8em; border-bottom: 1px solid $borderColor; padding-bottom: 0.3em; }
                    h2 { font-size: 1.5em; border-bottom: 1px solid $borderColor; padding-bottom: 0.3em; }
                    h3 { font-size: 1.25em; }
                    a { color: $linkColor; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    code {
                        background: $codeBg;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 0.9em;
                    }
                    pre {
                        background: $codeBg;
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                    pre code {
                        background: none;
                        padding: 0;
                    }
                    blockquote {
                        border-left: 3px solid $borderColor;
                        margin-left: 0;
                        padding-left: 16px;
                        color: ${fgColor}cc;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 1em 0;
                    }
                    th, td {
                        border: 1px solid $borderColor;
                        padding: 8px 12px;
                        text-align: left;
                    }
                    th {
                        background: $codeBg;
                        font-weight: 600;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 8px;
                    }
                    hr {
                        border: none;
                        border-top: 1px solid $borderColor;
                        margin: 1.5em 0;
                    }
                    ul, ol {
                        padding-left: 1.5em;
                    }
                    li {
                        margin: 0.25em 0;
                    }
                </style>
            </head>
            <body>
                <div id="content"></div>
                ${CdnAssets.MARKED.scriptTag()}
                ${CdnAssets.DOMPURIFY.scriptTag()}
                ${CdnAssets.HLJS_CORE.scriptTag()}
                ${CdnAssets.HLJS_COMMON.scriptTag()}
                <script>
                    marked.setOptions({ gfm: true, breaks: true });
                    const md = ${ArtifactWebContent.decodeBase64Js(contentLiteral)};
                    const html = DOMPurify.sanitize(marked.parse(md), { USE_PROFILES: { html: true } });
                    document.getElementById('content').innerHTML = html;
                    try { hljs.highlightAll(); } catch (e) { /* highlighting is decoration */ }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
