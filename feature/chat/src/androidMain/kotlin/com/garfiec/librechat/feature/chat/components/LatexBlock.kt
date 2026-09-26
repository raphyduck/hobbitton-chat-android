package com.garfiec.librechat.feature.chat.components

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebContent
import com.garfiec.librechat.feature.chat.components.artifact.CdnAssets
import com.garfiec.librechat.feature.chat.components.artifact.WebResourcePolicy
import com.garfiec.librechat.feature.chat.components.web.LockedDownWebViewClient
import com.garfiec.librechat.feature.chat.components.web.applyLockedDownSettings
import com.garfiec.librechat.feature.chat.components.web.loadIsolatedDocument

/**
 * Escapes a LaTeX string for safe embedding inside a JavaScript string literal.
 */
private fun escapeForJs(latex: String): String {
    return latex
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
}

/**
 * Converts an ARGB int color to a CSS rgba() string.
 */
private fun argbToCss(argb: Int): String {
    val a = ((argb shr 24) and 0xFF) / 255.0
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r, $g, $b, $a)"
}

/** Only the pinned KaTeX script, stylesheet and fonts from jsdelivr. */
private val KATEX_POLICY = WebResourcePolicy(setOf(CdnAssets.JSDELIVR_HOST), anyHttps = false)

private const val KATEX_CSP = "default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; " +
    "style-src 'unsafe-inline' https://cdn.jsdelivr.net; font-src https://cdn.jsdelivr.net; img-src data:; " +
    "${ArtifactWebContent.CSP_NO_FRAMES} form-action 'none'; base-uri 'none'; object-src 'none';"

/**
 * Builds the minimal HTML page that loads KaTeX from CDN and renders a LaTeX expression.
 *
 * `trust: false` (review C4, 26/09/2026): with `trust` on, `\href`/`\url` produce links of any
 * scheme and `\htmlStyle`/`\htmlData` emit attributes, from an element nobody suspects — an
 * equation in a reply. KaTeX renders those commands as errors instead, which is what the web
 * client does too.
 *
 * @param escapedLatex The LaTeX string already escaped via [escapeForJs].
 * @param displayMode true for block/display mode (centered, large), false for inline mode.
 * @param textColorCss CSS color string for the rendered math text.
 */
private fun buildKatexHtml(escapedLatex: String, displayMode: Boolean, textColorCss: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Security-Policy" content="$KATEX_CSP">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
${CdnAssets.KATEX_STYLE.stylesheetTag()}
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: transparent;
    color: $textColorCss;
    font-size: 18px;
    padding: 4px 0;
    ${if (displayMode) "text-align: center;" else "display: inline;"}
    overflow-x: auto;
    overflow-y: hidden;
  }
  .katex-display { margin: 0; padding: 0; }
  .katex { color: $textColorCss; }
  .katex-error { color: $textColorCss; font-family: monospace; font-size: 14px; }
</style>
</head>
<body>
<div id="math"></div>
${CdnAssets.KATEX_SCRIPT.scriptTag()}
<script>
try {
  katex.render("$escapedLatex", document.getElementById("math"), {
    displayMode: $displayMode,
    throwOnError: false,
    strict: false,
    trust: false
  });
} catch(e) {
  document.getElementById("math").textContent = "$escapedLatex";
}
// Report content height to Android for proper sizing
function reportHeight() {
  var h = document.body.scrollHeight;
  document.title = '' + h;
}
reportHeight();
new MutationObserver(reportHeight).observe(document.body, { childList: true, subtree: true });
</script>
</body>
</html>
    """.trimIndent()
}

// ── Plain-text renderers (lightweight, no WebView) ────────────────

@Composable
private fun NativeLatexBlock(
    latex: String,
    modifier: Modifier,
) {
    Text(
        text = latex,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(6.dp),
            )
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun NativeLatexInline(
    latex: String,
    modifier: Modifier,
) {
    Text(
        text = latex,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

// ── KaTeX (WebView) renderers ──────────────────────────────────────

@Composable
private fun KatexLatexBlock(
    latex: String,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val textColorCss = remember(textColorArgb) { argbToCss(textColorArgb) }
    val escapedLatex = remember(latex) { escapeForJs(latex) }
    val html = remember(escapedLatex, textColorCss) {
        buildKatexHtml(escapedLatex, displayMode = true, textColorCss = textColorCss)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                applyLockedDownSettings()
                webViewClient = KatexWebViewClient(uriHandler::openUri, surface = "LaTeX block")
                loadIsolatedDocument(html)
            }
        },
        update = { webView ->
            webView.loadIsolatedDocument(html)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

@Composable
private fun KatexLatexInline(
    latex: String,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val textColorCss = remember(textColorArgb) { argbToCss(textColorArgb) }
    val escapedLatex = remember(latex) { escapeForJs(latex) }
    val html = remember(escapedLatex, textColorCss) {
        buildKatexHtml(escapedLatex, displayMode = false, textColorCss = textColorCss)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                applyLockedDownSettings()
                webViewClient = KatexWebViewClient(uriHandler::openUri, surface = "LaTeX inline")
                loadIsolatedDocument(html)
            }
        },
        update = { webView ->
            webView.loadIsolatedDocument(html)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

/**
 * The locked-down client both KaTeX views share, plus the height read-back that sizes the view to
 * its content once the page has finished rendering.
 */
private class KatexWebViewClient(
    openExternally: (String) -> Unit,
    surface: String,
) : LockedDownWebViewClient(policy = { KATEX_POLICY }, openExternally = openExternally, surface = surface) {

    override fun onPageFinished(view: WebView, url: String?) {
        view.evaluateJavascript("document.body.scrollHeight") { heightStr ->
            val h = heightStr?.toIntOrNull()
            if (h != null && h > 0) {
                val density = view.resources.displayMetrics.density
                val layoutParams = view.layoutParams
                layoutParams.height = (h * density).toInt()
                view.layoutParams = layoutParams
            }
        }
    }
}

// ── Public API ──────────────────────────────────────────────────────

/**
 * Renders a LaTeX math expression as a display/block element (for `$$...$$` or `\[...\]`).
 * When [useKatex] is true, renders via KaTeX WebView (full rendering).
 * When false, shows the raw LaTeX source as styled monospace text (fast, no WebView).
 */
@Composable
actual fun LatexBlock(
    latex: String,
    modifier: Modifier,
    useKatex: Boolean,
) {
    if (useKatex) {
        KatexLatexBlock(latex = latex, modifier = modifier)
    } else {
        NativeLatexBlock(latex = latex, modifier = modifier)
    }
}

/**
 * Renders a LaTeX math expression inline (for `$...$` or `\(...\)`).
 * When [useKatex] is true, renders via KaTeX WebView (full rendering).
 * When false, shows the raw LaTeX source as styled monospace text (fast, no WebView).
 */
@Composable
actual fun LatexInline(
    latex: String,
    modifier: Modifier,
    useKatex: Boolean,
) {
    if (useKatex) {
        KatexLatexInline(latex = latex, modifier = modifier)
    } else {
        NativeLatexInline(latex = latex, modifier = modifier)
    }
}
