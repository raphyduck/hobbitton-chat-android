package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.feature.chat.components.artifact.LocalInlineArtifactPrefs
import com.garfiec.librechat.feature.chat.components.artifact.LocalMermaidRenderCache
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache

/**
 * Wraps the chat screen content with all chat-scoped CompositionLocals so that
 * each platform `Scaffold` doesn't repeat the provider list.
 */
@Composable
fun ChatRoot(
    inlineArtifactPrefs: InlineArtifactPrefs,
    mermaidRenderCache: MermaidRenderCache,
    parsedMarkdownCache: ParsedMarkdownCache,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalInlineArtifactPrefs provides inlineArtifactPrefs,
        LocalMermaidRenderCache provides mermaidRenderCache,
        LocalParsedMarkdownCache provides parsedMarkdownCache,
        content = content,
    )
}
