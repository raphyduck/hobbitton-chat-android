package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.components.CodeBlock
import com.garfiec.librechat.feature.chat.components.shareArtifact
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.code
import com.garfiec.librechat.feature.chat.resources.preview
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

private fun isPreviewableType(type: String): Boolean =
    when (ArtifactType.from(type)) {
        ArtifactType.MERMAID,
        ArtifactType.REACT,
        ArtifactType.SVG,
        ArtifactType.MARKDOWN,
        ArtifactType.HTML,
        ArtifactType.PLAIN,
        -> true
        ArtifactType.CODE -> false
    }

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
    val isPreviewable = isPreviewableType(currentArtifact.type)
    var selectedTab by remember { mutableIntStateOf(0) }

    val isDarkTheme = isSurfaceDark()

    val html = remember(currentArtifact, isDarkTheme) {
        ArtifactWebContent.buildHtml(currentArtifact.content, currentArtifact.type, isDarkTheme, inline = false)
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

            if (isPreviewable) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.code)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.preview)) },
                    )
                }
            }

            // Content body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !isPreviewable || selectedTab == 0 -> {
                        val codeScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
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
                        // Track what HTML is currently loaded to avoid reloading on every
                        // recomposition — version-nav swipes and Dialog animations otherwise
                        // re-trigger loadHTMLString and flash the WebView.
                        var loadedHtml by remember { mutableStateOf("") }
                        UIKitView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                                val config = WKWebViewConfiguration().apply {
                                    defaultWebpagePreferences.allowsContentJavaScript = true
                                }
                                val webView = WKWebView(
                                    frame = cValue { },
                                    configuration = config,
                                )
                                webView.setOpaque(false)
                                webView.loadHTMLString(
                                    html,
                                    baseURL = NSURL.URLWithString("https://cdn.jsdelivr.net"),
                                )
                                loadedHtml = html
                                webView
                            },
                            update = { webView ->
                                if (html != loadedHtml) {
                                    webView.loadHTMLString(
                                        html,
                                        baseURL = NSURL.URLWithString("https://cdn.jsdelivr.net"),
                                    )
                                    loadedHtml = html
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
