package com.librechat.android.feature.chat.components.artifact

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-specific artifact panel with WebView preview. */
@Composable
expect fun ArtifactPanel(
    artifact: Artifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    versions: List<Artifact> = listOf(artifact),
)
