package com.librechat.android.feature.chat.util

/**
 * Copies text to the system clipboard. Platform-specific implementation.
 */
expect fun copyToClipboard(text: String, label: String = "")
