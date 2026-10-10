package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Butler's one skin (10/10/2026): warm neutrals, a single brick accent, light and dark drawn
 * separately. Nothing here is generated from a seed or the wallpaper, so the app looks the same
 * on every phone and the accent never drifts with a theme engine.
 *
 * Roles the components lean on: `surface` is the page, `surfaceContainerLowest` the raised box
 * (the composer, a settings group), `surfaceContainerLow` the drawer, `secondaryContainer` the
 * person's bubble, `outlineVariant` the hairline, `onSurfaceVariant` the quiet text.
 */
val ButlerLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFBF6B49),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4DED3),
    onPrimaryContainer = Color(0xFF4A2516),
    inversePrimary = Color(0xFFD9896A),
    secondary = Color(0xFF6E6B64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E5DB),
    onSecondaryContainer = Color(0xFF1E1C19),
    tertiary = Color(0xFF5F8F6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEADF),
    onTertiaryContainer = Color(0xFF1C3A26),
    error = Color(0xFFB3473A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DDD8),
    onErrorContainer = Color(0xFF5A1F17),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1E1C19),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF1E1C19),
    surfaceVariant = Color(0xFFECE9E2),
    onSurfaceVariant = Color(0xFF6E6B64),
    surfaceTint = Color(0xFFBF6B49),
    inverseSurface = Color(0xFF33312D),
    inverseOnSurface = Color(0xFFF6F4EF),
    outline = Color(0xFFB5B0A5),
    outlineVariant = Color(0xFFDCD8CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F4EF),
    surfaceDim = Color(0xFFD8D4CB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFECE9E2),
    surfaceContainer = Color(0xFFE9E5DB),
    surfaceContainerHigh = Color(0xFFE3DFD4),
    surfaceContainerHighest = Color(0xFFDCD8CE),
)

val ButlerDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFD9896A),
    onPrimary = Color(0xFF3B1A0D),
    primaryContainer = Color(0xFF5B2E1D),
    onPrimaryContainer = Color(0xFFF6DACC),
    inversePrimary = Color(0xFFBF6B49),
    secondary = Color(0xFFA3A098),
    onSecondary = Color(0xFF1E1D1B),
    secondaryContainer = Color(0xFF2E2C28),
    onSecondaryContainer = Color(0xFFECE9E2),
    tertiary = Color(0xFF8DBB97),
    onTertiary = Color(0xFF0F2A18),
    tertiaryContainer = Color(0xFF2A4A33),
    onTertiaryContainer = Color(0xFFD5EBD9),
    error = Color(0xFFE88A7D),
    onError = Color(0xFF3C0E09),
    errorContainer = Color(0xFF5C241C),
    onErrorContainer = Color(0xFFF8DDD8),
    background = Color(0xFF1E1D1B),
    onBackground = Color(0xFFECE9E2),
    surface = Color(0xFF1E1D1B),
    onSurface = Color(0xFFECE9E2),
    surfaceVariant = Color(0xFF2E2C28),
    onSurfaceVariant = Color(0xFFA3A098),
    surfaceTint = Color(0xFFD9896A),
    inverseSurface = Color(0xFFECE9E2),
    inverseOnSurface = Color(0xFF1E1D1B),
    outline = Color(0xFF6E6B64),
    outlineVariant = Color(0xFF3A3833),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3833),
    surfaceDim = Color(0xFF1E1D1B),
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerLow = Color(0xFF171614),
    surfaceContainer = Color(0xFF292826),
    surfaceContainerHigh = Color(0xFF2E2C28),
    surfaceContainerHighest = Color(0xFF3A3833),
)
