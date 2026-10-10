package com.garfiec.librechat.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Butler's Material 3 theme: the fixed light or dark palette ([ButlerLightColors],
 * [ButlerDarkColors]), the serif-and-sans type scale ([butlerTypography]) and the shapes.
 *
 * There is one skin (10/10/2026). The seed-generated scheme and the wallpaper-based Material You
 * colors of the earlier builds are gone: the look must not depend on the phone.
 */
@Composable
fun LibreChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ButlerDarkColors else ButlerLightColors,
        typography = butlerTypography(),
        shapes = libreChatShapes,
        content = content,
    )
}
