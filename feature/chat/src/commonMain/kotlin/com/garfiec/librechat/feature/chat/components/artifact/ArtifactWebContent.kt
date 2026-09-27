package com.garfiec.librechat.feature.chat.components.artifact

import com.garfiec.librechat.core.model.TextFormat
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Builds the HTML document for an artifact preview. Used by both the fullscreen
 * [ArtifactPanel] and the [InlineArtifactView]. Set [inline] to true when
 * embedding inside a chat message: SVG/Markdown/Plain shrink their body
 * padding, Mermaid additionally disables htmlLabels so the SVG can round-trip
 * through Coil's SvgDecoder for the cache-harvest path. HTML and React
 * templates ignore the flag — the surrounding Compose Surface supplies their
 * padding.
 *
 * ### Isolation (review C1/C2/C8, 26/09/2026)
 * Every document here is loaded with a null base URL, so it runs in an opaque origin: no cookie
 * jar, no storage, no `https://cdn.jsdelivr.net` identity shared by all artifacts, conversations
 * and accounts on the device. The two templates whose document the artifact itself authors
 * (HTML, React) are additionally wrapped in a host page whose only child is an
 * `<iframe sandbox="allow-scripts" srcdoc="…">`: the sandbox forbids forms, popups and top
 * navigation by construction rather than by a `<meta>` policy the content could precede, and the
 * `srcdoc` document inherits the host's CSP, which the app writes first in its own `<head>`.
 * Templates the app authors (Markdown, Mermaid, SVG, code) keep a direct document with a hardened
 * `<meta>` CSP, and the natively enforced [WebResourcePolicy] sits under all of them.
 */
object ArtifactWebContent {

    fun buildHtml(
        content: String,
        type: String,
        isDarkTheme: Boolean,
        inline: Boolean = false,
    ): String {
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"

        // Office-doc previews must NOT route through the generic HTML path: that
        // would inject `content` as raw HTML unconditionally, bypassing the
        // textFormat==html security gate. The office card pre-builds the safe,
        // complete document via [buildOfficePreviewHtml] (which honors the gate)
        // and passes it here as `content`; return it unchanged.
        // SECURITY: office-MIME artifacts are pre-gated by OfficePreviewCard (the only
        // producer; previews arrive as attachments, not :::artifact directives). Any
        // future raw-text office-MIME artifact MUST gate via buildOfficePreviewHtml
        // before reaching here, or it would pass through unescaped.
        if (ArtifactType.isOfficePreviewMime(type)) {
            return content
        }

        return when (ArtifactType.from(type)) {
            // htmlLabels = !inline: only the inline cache-harvest path needs htmlLabels
            // disabled so the resulting SVG renders correctly through Coil 3's SvgDecoder
            // (no foreignObject). The fullscreen ArtifactPanel renders via the live
            // WebView+mermaid runtime so it keeps mermaid's default htmlLabels=true for
            // label fidelity (bold/italic, <br/>, nested spans).
            ArtifactType.MERMAID -> MermaidWebContent.buildHtml(content, isDarkTheme, inline, htmlLabels = !inline)
            ArtifactType.MARKDOWN, ArtifactType.PLAIN -> MarkdownWebContent.buildHtml(content, isDarkTheme, inline)
            ArtifactType.REACT -> buildReactHtml(content, bgColor, fgColor)
            ArtifactType.SVG -> buildSvgHtml(content, bgColor, inline)
            ArtifactType.HTML -> buildEnhancedHtml(content, bgColor, fgColor)
            ArtifactType.CODE -> buildPlainHtml(content, bgColor, fgColor, inline)
        }
    }

    /**
     * What the WebView showing the document [buildHtml] produced for [type] may fetch. Kept next
     * to the templates so a CDN added to one is added to the other in the same edit.
     */
    fun resourcePolicy(type: String): WebResourcePolicy {
        if (ArtifactType.isOfficePreviewMime(type)) return HTML_POLICY
        return when (ArtifactType.from(type)) {
            ArtifactType.MERMAID -> MermaidWebContent.RESOURCE_POLICY
            ArtifactType.MARKDOWN, ArtifactType.PLAIN -> MarkdownWebContent.RESOURCE_POLICY
            ArtifactType.REACT -> REACT_POLICY
            ArtifactType.HTML -> HTML_POLICY
            ArtifactType.SVG, ArtifactType.CODE -> WebResourcePolicy.NONE
        }
    }

    /** Tailwind for the artifact's own classes, `https:` images because an HTML artifact shows pictures. */
    private val HTML_POLICY = WebResourcePolicy(setOf(CdnAssets.TAILWIND_HOST), anyHttps = true)

    /** The ESM CDN for every import, Babel from jsdelivr, Tailwind, and `https:` images/fonts. */
    private val REACT_POLICY = WebResourcePolicy(
        setOf(CdnAssets.JSDELIVR_HOST, CdnAssets.ESM_HOST, CdnAssets.TAILWIND_HOST),
        anyHttps = true,
    )

    /**
     * Builds the document for a deferred office-doc preview (`TFilePreview`).
     *
     * SECURITY (load-bearing): the server's `text` is injected as live HTML
     * ONLY when [textFormat] is exactly `"html"` (the backend produced a
     * sanitized full-document preview). For `"text"`, null, or any other value
     * the content is plain text and is rendered through the escaping monospace
     * path — it is NEVER injected as HTML. Upstream's `TFile.textFormat` doc
     * explicitly warns against injecting the `text` format as HTML.
     */
    fun buildOfficePreviewHtml(
        text: String,
        textFormat: String?,
        isDarkTheme: Boolean,
        inline: Boolean = false,
    ): String {
        val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
        val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
        return if (textFormat == TextFormat.HTML) {
            buildEnhancedHtml(text, bgColor, fgColor)
        } else {
            buildPlainHtml(text, bgColor, fgColor, inline)
        }
    }

    /**
     * Directives every template carries. `form-action 'none'` stops a form from posting anywhere
     * (a POST is a navigation `shouldOverrideUrlLoading` never sees), `base-uri 'none'` stops a
     * `<base>` from re-rooting relative URLs, `object-src 'none'` closes plugins.
     */
    private const val CSP_COMMON_TAIL = "form-action 'none'; base-uri 'none'; object-src 'none';"

    /**
     * For the sandbox hosts: nested frames may only be `about:` ones (`about:blank`,
     * `about:srcdoc`), which inherit this very policy and the sandbox — never a remote page. The
     * host's own `srcdoc` child is such a frame, which is why this is not `'none'`.
     */
    private const val CSP_SANDBOX_FRAMES = "frame-src about:;"

    /** For the app-authored templates, which frame nothing. */
    internal const val CSP_NO_FRAMES = "frame-src 'none';"

    /** The policy the HTML sandbox host declares and its `srcdoc` child inherits. */
    private const val HTML_CSP = "default-src 'none'; script-src 'unsafe-inline' https://cdn.tailwindcss.com; " +
        "style-src 'unsafe-inline'; img-src data: blob: https:; connect-src https://cdn.tailwindcss.com; " +
        "$CSP_SANDBOX_FRAMES $CSP_COMMON_TAIL"

    /** As [HTML_CSP], plus what compiling JSX in-browser and importing from the ESM CDN need. */
    private const val REACT_CSP = "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' blob: " +
        "https://esm.sh https://cdn.jsdelivr.net https://cdn.tailwindcss.com; style-src 'unsafe-inline' https:; " +
        "img-src data: blob: https:; font-src data: https:; connect-src https://esm.sh https://cdn.tailwindcss.com; " +
        "$CSP_SANDBOX_FRAMES $CSP_COMMON_TAIL"

    // Security note: HTML artifacts intentionally render unsanitized HTML content.
    // This is by design — HTML artifacts are meant to be rendered as-is. What
    // contains them is the sandboxed frame built by [sandboxHost], not the markup.
    private fun buildEnhancedHtml(content: String, bgColor: String, fgColor: String): String {
        val hasHtmlTag = content.contains("<html", ignoreCase = true) ||
            content.contains("<!DOCTYPE", ignoreCase = true)

        val document = if (hasHtmlTag) {
            val themeStyle = """
                <style>:root { --bg: $bgColor; --fg: $fgColor; } html, body { max-width: 100%; overflow-x: hidden; } body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; } img, svg, video, iframe { max-width: 100%; height: auto; }</style>
                ${CdnAssets.TAILWIND_SCRIPT_TAG}
            """.trimIndent()
            if (content.contains("<head>", ignoreCase = true)) {
                content.replaceFirst(
                    Regex("<head>", RegexOption.IGNORE_CASE),
                    "<head>$themeStyle",
                )
            } else if (content.contains("<head ", ignoreCase = true)) {
                val headMatch = Regex("<head\\s[^>]*>", RegexOption.IGNORE_CASE).find(content)
                if (headMatch != null) {
                    content.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, themeStyle)
                } else {
                    "$themeStyle\n$content"
                }
            } else {
                "$themeStyle\n$content"
            }
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                ${CdnAssets.TAILWIND_SCRIPT_TAG}
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
                    img, svg, video, iframe { max-width: 100%; height: auto; }
                </style>
            </head>
            <body>$content</body>
            </html>
            """.trimIndent()
        }
        return sandboxHost(document, HTML_CSP, bgColor)
    }

    /**
     * The host page for a document the artifact wrote: the app's CSP first in its own `<head>`,
     * then a single frame that fills the WebView and carries the document as `srcdoc`.
     *
     * `sandbox="allow-scripts"` and nothing else: no `allow-same-origin` (the frame's origin stays
     * opaque — no storage, no cookies), no `allow-forms`, no `allow-popups`, no
     * `allow-top-navigation`. Scripts from CDNs still load, since a classic `<script src>` needs
     * no CORS and the ESM CDN serves `Access-Control-Allow-Origin: *`. The frame's own
     * navigations still reach `shouldOverrideUrlLoading`, which refuses them.
     */
    internal fun sandboxHost(document: String, csp: String, bgColor: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta http-equiv="Content-Security-Policy" content="$csp">
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; background: $bgColor; }
                iframe { border: 0; width: 100%; height: 100%; display: block; }
            </style>
        </head>
        <body><iframe sandbox="allow-scripts" srcdoc="${escapeHtml(document)}"></iframe></body>
        </html>
    """.trimIndent()

    // Security note: SVG content is rendered unsanitized because SVG artifacts are
    // designed to display user-provided vector graphics. The document is the app's,
    // its CSP comes first, and no script source is allowed at all.
    private fun buildSvgHtml(content: String, bgColor: String, inline: Boolean): String {
        val padding = if (inline) "4px" else "16px"
        val minHeight = if (inline) "0" else "100vh"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; $CSP_NO_FRAMES $CSP_COMMON_TAIL">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body {
                        margin: 0;
                        padding: $padding;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: $minHeight;
                        background: $bgColor;
                    }
                    svg, .svg-container {
                        width: 100%;
                        height: auto;
                        max-width: 100%;
                    }
                </style>
            </head>
            <body><div class="svg-container">$content</div></body>
            </html>
        """.trimIndent()
    }

    // Security note: React artifacts intentionally render unsanitized, model-generated
    // JSX/JS, inside the sandboxed frame built by [sandboxHost]. Imports are resolved at
    // runtime via a generated import map pointing at an ESM CDN, so ANY npm package the
    // model reaches for resolves without per-library handling — mirroring the web app's
    // Sandpack bundler. Those modules cannot carry an integrity hash (the CDN rewrites
    // them per request); the exact React pin and the sandbox are what bound them.
    private const val ESM_CDN = "https://esm.sh"

    /** React version pinned across the import map so the runner, the artifact
     *  module, and every externalized library dep resolve to one React instance
     *  (mismatched copies break hooks with "invalid hook call"). */
    private const val REACT_PIN = "18.3.1"

    /** Captures the module specifier of every `import … from 'X'` and bare
     *  side-effect `import 'X'`. */
    private val MODULE_SPECIFIER = Regex("""(?:from|import)\s*['"]([^'"]+)['"]""")

    /** Always-present entries so React itself resolves to a single pinned copy;
     *  react-dom and all third-party libs externalize onto these via `?external`. */
    private val CORE_IMPORTS = linkedMapOf(
        "react" to "$ESM_CDN/react@$REACT_PIN",
        "react/jsx-runtime" to "$ESM_CDN/react@$REACT_PIN/jsx-runtime",
        "react-dom" to "$ESM_CDN/react-dom@$REACT_PIN?external=react",
        "react-dom/client" to "$ESM_CDN/react-dom@$REACT_PIN/client?external=react",
    )

    /**
     * Builds an import map covering every bare module specifier the artifact
     * imports. Relative (`./`, `/`) and absolute-URL specifiers are left untouched.
     * Each bare package maps to the ESM CDN with React/ReactDOM externalized so it
     * shares the single pinned instance. This is the general-purpose mechanism:
     * the model can import any npm package and it resolves with no special-casing.
     */
    private fun buildReactImportMap(content: String): String {
        val entries = LinkedHashMap(CORE_IMPORTS)
        MODULE_SPECIFIER.findAll(content)
            .map { it.groupValues[1] }
            .filter { spec ->
                // Bare npm specifiers only: skip relative ('.'/'..'), absolute and
                // protocol-relative ('/', '//') paths, and any URL scheme ('http:',
                // 'data:', 'node:', …) — none of which a valid package name contains.
                spec.isNotBlank() &&
                    !spec.startsWith(".") &&
                    !spec.startsWith("/") &&
                    !spec.contains(":") &&
                    spec !in CORE_IMPORTS
            }
            .forEach { spec -> entries[spec] = "$ESM_CDN/$spec?external=react,react-dom" }
        val imports = entries.entries.joinToString(",\n      ") { (k, v) -> "\"$k\": \"$v\"" }
        return "{\n    \"imports\": {\n      $imports\n    }\n  }"
    }

    private fun buildReactHtml(content: String, bgColor: String, fgColor: String): String {
        val importMap = buildReactImportMap(content)
        // Embed the source as base64 so arbitrary JSX — including `</script>`,
        // backticks, or `${'$'}{...}` — round-trips with zero HTML/JS escaping
        // hazards. The runner decodes, compiles JSX, and imports it as a real
        // ES module so the artifact's own `import`/`export` statements work
        // verbatim against the import map (no source rewriting).
        val sourceB64 = base64Literal(content)

        val document = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script type="importmap">$importMap</script>
                ${CdnAssets.BABEL_STANDALONE.scriptTag()}
                ${CdnAssets.TAILWIND_SCRIPT_TAG}
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
                    img, svg, video, iframe { max-width: 100%; height: auto; }
                    #error-display {
                        display: none;
                        padding: 16px;
                        margin: 16px;
                        background: #B3261E22;
                        border: 1px solid #B3261E;
                        border-radius: 8px;
                        font-family: monospace;
                        font-size: 13px;
                        white-space: pre-wrap;
                        color: $fgColor;
                    }
                </style>
            </head>
            <body>
                <div id="root"></div>
                <div id="error-display"></div>
                <script type="module">
                    const root = document.getElementById('root');
                    const errDiv = document.getElementById('error-display');
                    function showError(msg) {
                        if (root.hasChildNodes()) return;
                        errDiv.style.display = 'block';
                        errDiv.textContent = 'Component failed to render:\n' + msg;
                    }
                    window.addEventListener('error', function(e) { showError(e.message || 'Unknown error'); });
                    window.addEventListener('unhandledrejection', function(e) {
                        showError((e.reason && e.reason.message) || String(e.reason));
                    });
                    try {
                        const ReactNS = await import('react');
                        const React = ReactNS.default || ReactNS;
                        const { createRoot } = await import('react-dom/client');
                        const source = new TextDecoder().decode(
                            Uint8Array.from(atob('$sourceB64'), function(c) { return c.charCodeAt(0); })
                        );
                        const compiled = Babel.transform(source, {
                            presets: [['react', { runtime: 'automatic', development: false }]],
                            filename: 'artifact.jsx',
                            sourceType: 'module',
                        }).code;
                        const blobUrl = URL.createObjectURL(new Blob([compiled], { type: 'text/javascript' }));
                        const mod = await import(blobUrl);
                        const Component = mod.default ||
                            Object.values(mod).find(function(v) { return typeof v === 'function'; });
                        if (!Component) {
                            throw new Error('No React component is exported. Add `export default`.');
                        }
                        createRoot(root).render(React.createElement(Component));
                    } catch (e) {
                        showError((e && e.message) || String(e));
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return sandboxHost(document, REACT_CSP, bgColor)
    }

    private fun buildPlainHtml(content: String, bgColor: String, fgColor: String, inline: Boolean): String {
        val padding = if (inline) "8px" else "16px"
        val escaped = escapeHtml(content)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; $CSP_NO_FRAMES $CSP_COMMON_TAIL">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    html, body { max-width: 100%; overflow-x: hidden; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: $padding; font-family: monospace; font-size: 13px; }
                    pre { white-space: pre-wrap; word-wrap: break-word; margin: 0; }
                </style>
            </head>
            <body><pre>$escaped</pre></body>
            </html>
        """.trimIndent()
    }

    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /**
     * The transport every app-authored template uses to hand untrusted text to its own script:
     * base64 in a single-quoted literal, decoded with `TextDecoder`. Nothing in the content can
     * close the `<script>` element or the literal, whatever it contains (review C3).
     */
    @OptIn(ExperimentalEncodingApi::class)
    internal fun base64Literal(content: String): String = Base64.encode(content.encodeToByteArray())

    /** The JavaScript that turns a [base64Literal] back into the original string. */
    internal fun decodeBase64Js(literal: String): String =
        "new TextDecoder().decode(Uint8Array.from(atob('$literal'), function(c) { return c.charCodeAt(0); }))"
}
