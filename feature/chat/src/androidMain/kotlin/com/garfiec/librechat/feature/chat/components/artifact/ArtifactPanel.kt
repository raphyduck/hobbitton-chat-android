package com.garfiec.librechat.feature.chat.components.artifact

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import librechat_android.feature.chat.generated.resources.Res
import librechat_android.feature.chat.generated.resources.*
import com.garfiec.librechat.feature.chat.components.CodeBlock
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ArtifactPanel(
    artifact: Artifact,
    onDismiss: () -> Unit,
    modifier: Modifier,
    versions: List<Artifact>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.fillMaxSize(),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        ArtifactPanelContent(
            artifact = artifact,
            versions = versions,
            sheetState = sheetState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        )
    }
}

private fun isPreviewableType(type: String): Boolean {
    return type.contains("html") ||
        type.contains("svg") ||
        type.contains("react") ||
        type.contains("mermaid") ||
        type.contains("markdown") ||
        type == "text/md" ||
        type == "text/plain" ||
        type.contains("code-html")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtifactPanelContent(
    artifact: Artifact,
    versions: List<Artifact>,
    sheetState: SheetState,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentVersionIndex by remember {
        mutableIntStateOf(versions.indexOfFirst { it.version == artifact.version }.coerceAtLeast(0))
    }
    var showFullscreen by remember { mutableIntStateOf(-1) } // -1 = hidden, 0 = code, 1 = preview

    val currentArtifact = versions.getOrElse(currentVersionIndex) { artifact }
    val isPreviewable = isPreviewableType(currentArtifact.type)
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        // Title row with version nav and action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentArtifact.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (versions.size > 1) {
                ArtifactVersionNav(
                    currentIndex = currentVersionIndex,
                    totalVersions = versions.size,
                    onPrevious = { currentVersionIndex-- },
                    onNext = { currentVersionIndex++ },
                )
            }
        }

        // Action row: share + fullscreen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    ArtifactDownloadHelper.share(
                        context = context,
                        artifact = currentArtifact,
                    )
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(Res.string.cd_share_artifact),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { showFullscreen = selectedTab },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = stringResource(Res.string.cd_fullscreen),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isPreviewable) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(Res.string.code)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        scope.launch { sheetState.expand() }
                    },
                    text = { Text(stringResource(Res.string.preview)) },
                )
            }
        }

        when {
            !isPreviewable || selectedTab == 0 -> {
                val codeScrollState = rememberScrollState()
                val codeScrollBlocker = remember {
                    SheetScrollBlocker(codeScrollState)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                        .nestedScroll(codeScrollBlocker)
                        .verticalScroll(codeScrollState),
                ) {
                    CodeBlock(
                        code = currentArtifact.content,
                        language = currentArtifact.language,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            selectedTab == 1 -> {
                ArtifactPreviewWebView(
                    content = currentArtifact.content,
                    type = currentArtifact.type,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                )
            }
        }
    }

    // Fullscreen dialog
    if (showFullscreen >= 0) {
        Dialog(
            onDismissRequest = { showFullscreen = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                when {
                    !isPreviewable || showFullscreen == 0 -> {
                        CodeBlock(
                            code = currentArtifact.content,
                            language = currentArtifact.language,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                    showFullscreen == 1 -> {
                        ArtifactPreviewWebView(
                            content = currentArtifact.content,
                            type = currentArtifact.type,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 48.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { showFullscreen = -1 },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cd_close_fullscreen),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ArtifactPreviewWebView(
    content: String,
    type: String,
    isDarkTheme: Boolean,
    modifier: Modifier,
) {
    val bgColor = MaterialTheme.colorScheme.surface.toArgb()
    var isLoading by remember { mutableStateOf(true) }

    val html = remember(content, type, isDarkTheme) {
        buildWebViewHtml(content, type, isDarkTheme)
    }

    // Track what HTML is currently loaded to avoid reloading on every recomposition.
    // Without this, the isLoading state changes from WebView callbacks trigger
    // recompositions that call update, which reloads the page, creating an infinite loop
    // that prevents external scripts (like mermaid.js) from ever finishing.
    var loadedHtml by remember { mutableStateOf("") }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Prevent touch events from propagating to the bottom sheet
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false // let the WebView handle the event normally
                    }
                    setBackgroundColor(bgColor)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 80) isLoading = false
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                view?.loadDataWithBaseURL(
                                    null,
                                    buildErrorHtml(error?.description?.toString() ?: "Unknown error"),
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?,
                        ) {
                            handler?.cancel()
                            Logger.w { "SSL error in artifact WebView: ${error?.primaryError}" }
                        }
                    }
                    loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                    loadedHtml = html
                }
            },
            update = { webView ->
                webView.setBackgroundColor(bgColor)
                if (html != loadedHtml) {
                    webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                    loadedHtml = html
                }
            },
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
    }
}

private fun buildWebViewHtml(content: String, type: String, isDarkTheme: Boolean): String {
    val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
    val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"

    return when {
        type.contains("mermaid") -> MermaidWebContent.buildHtml(content, isDarkTheme)
        type.contains("markdown") || type == "text/md" -> MarkdownWebContent.buildHtml(content, isDarkTheme)
        type == "text/plain" -> MarkdownWebContent.buildHtml(content, isDarkTheme)
        type.contains("react") -> buildReactHtml(content, bgColor, fgColor)
        type.contains("svg") -> buildSvgHtml(content, bgColor)
        type.contains("html") || type.contains("code-html") -> buildEnhancedHtml(content, bgColor, fgColor)
        else -> {
            val escapedContent = escapeHtml(content)
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline';">
                <style>
                    :root { --bg: $bgColor; --fg: $fgColor; }
                    body { background: var(--bg); color: var(--fg); margin: 0; padding: 16px; }
                </style>
            </head>
            <body><pre>$escapedContent</pre></body>
            </html>
            """.trimIndent()
        }
    }
}

// Security note: HTML artifacts intentionally render unsanitized HTML content.
// This is by design — HTML artifacts are meant to be rendered as-is. The WebView
// is sandboxed with a Content Security Policy restricting script/resource origins.
private fun buildEnhancedHtml(content: String, bgColor: String, fgColor: String): String {
    // If content already contains <html> or <!DOCTYPE>, inject Tailwind + theme vars
    val hasHtmlTag = content.contains("<html", ignoreCase = true) ||
        content.contains("<!DOCTYPE", ignoreCase = true)

    if (hasHtmlTag) {
        val themeStyle = """
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.tailwindcss.com; style-src 'unsafe-inline'; img-src data: blob: https:; connect-src https://cdn.tailwindcss.com;">
            <style>:root { --bg: $bgColor; --fg: $fgColor; } body { background: var(--bg); color: var(--fg); }</style>
            <script src="https://cdn.tailwindcss.com"></script>
        """.trimIndent()
        // Insert after <head> if present, otherwise before content
        return if (content.contains("<head>", ignoreCase = true)) {
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
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.tailwindcss.com; style-src 'unsafe-inline'; img-src data: blob: https:; connect-src https://cdn.tailwindcss.com;">
            <script src="https://cdn.tailwindcss.com"></script>
            <style>
                :root { --bg: $bgColor; --fg: $fgColor; }
                body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
            </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()
}

// Security note: SVG content is rendered unsanitized because SVG artifacts are
// designed to display user-provided vector graphics. CSP restricts script execution.
private fun buildSvgHtml(content: String, bgColor: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:;">
            <style>
                body {
                    margin: 0;
                    padding: 16px;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
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

// Security note: React artifacts intentionally render unsanitized content because they
// must execute user-provided JSX/JS code. 'unsafe-inline' and 'unsafe-eval' are required
// in script-src for Babel transpilation and React rendering. CSP restricts script origins
// to specific CDN hosts (unpkg.com, cdn.tailwindcss.com).
private fun buildReactHtml(content: String, bgColor: String, fgColor: String): String {
    // Preprocess: strip ES module imports/exports for browser compatibility.
    // React/ReactDOM are loaded as UMD globals, so `import { useState } from 'react'`
    // becomes a destructuring from the global React object.
    val processed = content
        .replace(Regex("""import\s*\{([^}]+)\}\s*from\s*['"]react['"];?""")) {
            "const {${it.groupValues[1]}} = React;"
        }
        .replace(Regex("""import\s*React\s*from\s*['"]react['"];?"""), "")
        .replace(Regex("""import\s*\{([^}]+)\}\s*from\s*['"]react-dom['"];?""")) {
            "const {${it.groupValues[1]}} = ReactDOM;"
        }
        .replace(Regex("""export\s+default\s+function\s+"""), "function ")
        .replace(Regex("""export\s+default\s+"""), "const _DefaultExport = ")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://cdn.tailwindcss.com; style-src 'unsafe-inline'; img-src data: blob: https:; connect-src https://cdn.tailwindcss.com;">
            <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
            <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
            <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
            <script src="https://cdn.tailwindcss.com"></script>
            <style>
                :root { --bg: $bgColor; --fg: $fgColor; }
                body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
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
            <script>
                window.addEventListener('error', function(e) {
                    var errDiv = document.getElementById('error-display');
                    if (errDiv && !document.getElementById('root').hasChildNodes()) {
                        errDiv.style.display = 'block';
                        errDiv.textContent = 'Component compilation failed:\n' + (e.message || 'Unknown error');
                    }
                });
            </script>
            <script type="text/babel">
                try {
                    const { useState, useEffect, useRef, useMemo, useCallback, useReducer, useContext, createContext } = React;

                    $processed

                    const _root = ReactDOM.createRoot(document.getElementById('root'));
                    // Find the component to render: look for common names or _DefaultExport
                    const _Component = typeof _DefaultExport !== 'undefined' ? _DefaultExport
                        : typeof App !== 'undefined' ? App
                        : typeof Counter !== 'undefined' ? Counter
                        : typeof Main !== 'undefined' ? Main
                        : typeof Component !== 'undefined' ? Component
                        : null;
                    if (_Component) {
                        if (typeof _Component === 'function') {
                            _root.render(React.createElement(_Component));
                        } else {
                            _root.render(_Component);
                        }
                    }
                } catch (e) {
                    var errDiv = document.getElementById('error-display');
                    errDiv.style.display = 'block';
                    errDiv.textContent = 'Component compilation failed:\n' + e.message;
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Intercepts all vertical scroll and manually dispatches it to the given [ScrollState],
 * then reports it all as consumed so the parent ModalBottomSheet never receives it.
 * This isolates the content's scrolling from the sheet's drag gesture.
 */
private class SheetScrollBlocker(private val scrollState: ScrollState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Manually scroll the content and consume ALL vertical delta
        // so none reaches the sheet's drag handler.
        if (available.y != 0f) {
            scrollState.dispatchRawDelta(-available.y)
        }
        return Offset(0f, available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return Offset(0f, available.y)
    }
}

/**
 * Escapes HTML special characters to prevent XSS when embedding user-supplied
 * or error-derived text into WebView HTML.
 */
private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun buildErrorHtml(errorMessage: String): String {
    val safeMessage = escapeHtml(errorMessage)
    return """
        <!DOCTYPE html>
        <html>
        <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;padding:16px;font-family:sans-serif;background:#FFFBFE;">
            <div style="padding:16px;background:#B3261E22;border:1px solid #B3261E;border-radius:8px;">
                <strong>Failed to load preview</strong><br>
                <span style="font-size:13px;color:#666;">$safeMessage</span>
            </div>
        </body>
        </html>
    """.trimIndent()
}
