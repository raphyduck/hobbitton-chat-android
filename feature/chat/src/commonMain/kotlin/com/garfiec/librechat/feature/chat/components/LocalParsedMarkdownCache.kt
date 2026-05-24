package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf

val LocalParsedMarkdownCache = staticCompositionLocalOf<ParsedMarkdownCache> {
    error("LocalParsedMarkdownCache not provided; wrap chat content in ChatRoot { }")
}
