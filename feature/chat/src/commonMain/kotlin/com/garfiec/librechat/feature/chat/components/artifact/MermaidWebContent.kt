package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Builds an HTML page that renders a Mermaid diagram using the Mermaid.js CDN.
 * Includes zoom controls and theme support.
 *
 * Uses mermaid v10 (UMD build) because v11+ ships ESM-only, which fails in
 * Android WebView's `loadDataWithBaseURL` with "Unexpected token '{'".
 *
 * The diagram source reaches the page as a base64 literal, never spliced into a template string
 * (review C3, 26/09/2026 — see [MarkdownWebContent]). `securityLevel` is `strict`, as the chat's
 * own `MermaidDiagram` already had it: `loose` let a label carry HTML and a `click` directive
 * carry any URL, in a card the user reads as a picture.
 */
object MermaidWebContent {

    /** Nothing but the pinned runtime from jsdelivr. */
    val RESOURCE_POLICY = WebResourcePolicy(setOf(CdnAssets.JSDELIVR_HOST), anyHttps = false)

    private const val CSP = "default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; " +
        "style-src 'unsafe-inline'; img-src data:; " +
        "${ArtifactWebContent.CSP_NO_FRAMES} form-action 'none'; base-uri 'none'; object-src 'none';"

    fun buildHtml(
        mermaidCode: String,
        isDarkTheme: Boolean,
        inline: Boolean = false,
        htmlLabels: Boolean = false,
    ): String {
        val theme = if (isDarkTheme) "dark" else "default"
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
        val btnBg = if (isDarkTheme) "#332D41" else "#E8DEF8"
        val bodyPadding = if (inline) "4px" else "16px"
        val zoomDisplay = if (inline) "none" else "flex"
        val codeLiteral = ArtifactWebContent.base64Literal(mermaidCode)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta http-equiv="Content-Security-Policy" content="$CSP">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body {
                        margin: 0;
                        padding: $bodyPadding;
                        background: $bgColor;
                        color: $fgColor;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        font-family: -apple-system, sans-serif;
                    }
                    #mermaid-container {
                        transform-origin: top center;
                        transition: transform 0.2s ease;
                        overflow: visible;
                        width: 100%;
                        display: flex;
                        justify-content: center;
                    }
                    #mermaid-container svg {
                        max-width: 100%;
                        height: auto;
                    }
                    .zoom-controls {
                        position: fixed;
                        bottom: 12px;
                        right: 12px;
                        display: $zoomDisplay;
                        gap: 8px;
                        z-index: 10;
                    }
                    .zoom-btn {
                        width: 36px;
                        height: 36px;
                        border-radius: 50%;
                        border: none;
                        background: $btnBg;
                        color: $fgColor;
                        font-size: 18px;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    #error-display {
                        display: none;
                        padding: 12px;
                        background: #B3261E22;
                        border: 1px solid #B3261E;
                        border-radius: 8px;
                        color: $fgColor;
                        font-family: monospace;
                        font-size: 13px;
                        white-space: pre-wrap;
                        width: 100%;
                        box-sizing: border-box;
                    }
                </style>
            </head>
            <body>
                <div id="mermaid-container"></div>
                <div id="error-display"></div>
                <div class="zoom-controls">
                    <button class="zoom-btn" onclick="zoom(-0.2)">-</button>
                    <button class="zoom-btn" onclick="zoom(0.2)">+</button>
                </div>
                <script>
                    // Polyfills for older Android WebView (Chrome 91 on API 31)
                    if (typeof structuredClone === 'undefined') {
                        window.structuredClone = function(obj) {
                            return JSON.parse(JSON.stringify(obj));
                        };
                    }
                    if (!Object.hasOwn) {
                        Object.hasOwn = function(obj, prop) {
                            return Object.prototype.hasOwnProperty.call(obj, prop);
                        };
                    }
                    if (typeof Array.prototype.at === 'undefined') {
                        Array.prototype.at = function(n) {
                            n = Math.trunc(n) || 0;
                            if (n < 0) n += this.length;
                            return this[n];
                        };
                    }
                    if (typeof String.prototype.at === 'undefined') {
                        String.prototype.at = function(n) {
                            n = Math.trunc(n) || 0;
                            if (n < 0) n += this.length;
                            return this[n];
                        };
                    }
                    if (typeof String.prototype.replaceAll === 'undefined') {
                        String.prototype.replaceAll = function(search, replacement) {
                            return this.split(search).join(replacement);
                        };
                    }
                </script>
                ${CdnAssets.MERMAID.scriptTag()}
                <script>
                    var scale = 1;
                    function zoom(delta) {
                        scale = Math.max(0.2, Math.min(3, scale + delta));
                        document.getElementById('mermaid-container').style.transform = 'scale(' + scale + ')';
                    }

                    mermaid.initialize({
                        startOnLoad: false,
                        theme: '$theme',
                        securityLevel: 'strict',
                        flowchart: { htmlLabels: $htmlLabels }
                    });

                    (async function() {
                        var code = ${ArtifactWebContent.decodeBase64Js(codeLiteral)};
                        try {
                            var result = await mermaid.render('mermaid-graph', code);
                            document.getElementById('mermaid-container').innerHTML = result.svg;
                            try {
                                if (window.MermaidBridge && window.MermaidBridge.onSvg) {
                                    window.MermaidBridge.onSvg(result.svg);
                                }
                            } catch (e) { /* swallow; bridge failure must not break visible render */ }
                        } catch (e) {
                            document.getElementById('error-display').style.display = 'block';
                            document.getElementById('error-display').textContent = 'Mermaid parse error:\n' + e.message + '\n\nRaw code:\n' + code;
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
