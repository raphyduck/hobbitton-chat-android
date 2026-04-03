package com.librechat.android.feature.chat.components.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.librechat.android.feature.chat.components.shareArtifact
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ArtifactPanel(
    artifact: Artifact,
    onDismiss: () -> Unit,
    modifier: Modifier,
    versions: List<Artifact>,
) {
    var currentVersionIndex by remember {
        mutableIntStateOf(versions.indexOfFirst { it.identifier == artifact.identifier && it.version == artifact.version }.coerceAtLeast(0))
    }
    val currentArtifact = versions.getOrElse(currentVersionIndex) { artifact }

    val isDarkTheme = MaterialTheme.colorScheme.surface.let { color ->
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        (r * 0.299 + g * 0.587 + b * 0.114) < 128
    }

    val html = remember(currentArtifact, isDarkTheme) {
        buildArtifactHtml(currentArtifact, isDarkTheme)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = currentArtifact.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (versions.size > 1) {
                    ArtifactVersionNav(
                        currentIndex = currentVersionIndex,
                        totalVersions = versions.size,
                        onPrevious = {
                            if (currentVersionIndex > 0) currentVersionIndex--
                        },
                        onNext = {
                            if (currentVersionIndex < versions.size - 1) currentVersionIndex++
                        },
                    )
                }
                IconButton(
                    onClick = {
                        shareArtifact(
                            title = currentArtifact.title,
                            content = currentArtifact.content,
                            language = currentArtifact.language ?: "",
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // WebView content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                UIKitView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val config = WKWebViewConfiguration().apply {
                            defaultWebpagePreferences.allowsContentJavaScript = true
                        }
                        val webView = WKWebView(
                            frame = kotlinx.cinterop.cValue { },
                            configuration = config,
                        )
                        webView.setOpaque(false)
                        webView.loadHTMLString(
                            html,
                            baseURL = NSURL.URLWithString("https://cdn.jsdelivr.net"),
                        )
                        webView
                    },
                    update = { webView ->
                        webView.loadHTMLString(
                            html,
                            baseURL = NSURL.URLWithString("https://cdn.jsdelivr.net"),
                        )
                    },
                )
            }
        }
    }
}

private fun buildArtifactHtml(artifact: Artifact, isDarkTheme: Boolean): String {
    return when {
        artifact.type.contains("mermaid") -> {
            MermaidWebContent.buildHtml(artifact.content, isDarkTheme)
        }
        artifact.type.contains("markdown") || artifact.type == "text/md" -> {
            MarkdownWebContent.buildHtml(artifact.content, isDarkTheme)
        }
        artifact.type.contains("html") || artifact.type.contains("react") || artifact.type.contains("svg") -> {
            // Render HTML/React/SVG directly
            val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
            val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 16px; background: $bgColor; color: $fgColor; font-family: -apple-system, system-ui, sans-serif; }
                </style>
            </head>
            <body>${artifact.content}</body>
            </html>
            """.trimIndent()
        }
        else -> {
            // Plain text / code
            val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFBFE"
            val fgColor = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"
            val escaped = artifact.content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 16px; background: $bgColor; color: $fgColor; font-family: monospace; }
                    pre { white-space: pre-wrap; word-wrap: break-word; }
                </style>
            </head>
            <body><pre>$escaped</pre></body>
            </html>
            """.trimIndent()
        }
    }
}
