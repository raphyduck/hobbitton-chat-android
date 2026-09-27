package com.garfiec.librechat.core.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Nothing on iOS: the engine graph is Android-only (D-034), and the login screen only offers the
 * embedded portal where its launcher says it can host one. Present so the common screens compile.
 */
@Composable
actual fun PortalWebView(
    url: String?,
    onNavigation: (url: String) -> Boolean,
    modifier: Modifier,
) {
    Box(modifier)
}
