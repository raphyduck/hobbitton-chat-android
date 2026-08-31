package com.garfiec.librechat.core.ui.util

/**
 * Copies text to the system clipboard.
 *
 * Here rather than in a feature because **two features copy**: the chat's code blocks have had a
 * copy button since the start, and a mission's transcript gained one on 31/08/2026. Feature modules
 * cannot see each other, so the alternative was a second `expect`/`actual` pair — and a second
 * place for the Android side's Koin lookup of the `Context` to go wrong.
 */
expect fun copyToClipboard(text: String, label: String = "")
