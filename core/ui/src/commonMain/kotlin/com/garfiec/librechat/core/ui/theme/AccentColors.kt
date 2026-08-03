package com.garfiec.librechat.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.DEFAULT_ACCENT_SEED_ARGB

/**
 * The default accent seed color (the turquoise brand hue of the app icon, canonical value
 * in [DEFAULT_ACCENT_SEED_ARGB]). Used to generate the Material 3 scheme when the user has
 * not chosen a custom accent and wallpaper-based dynamic color is off.
 */
val DefaultAccentSeed = Color(DEFAULT_ACCENT_SEED_ARGB)

/**
 * Curated set of accent seed colors offered in the picker. The full Material 3
 * [androidx.compose.material3.ColorScheme] is generated from whichever seed the
 * user selects, so these are source colors, not final role colors. Turquoise
 * (the default) is listed first.
 */
val AccentColorPresets: List<Color> = listOf(
    DefaultAccentSeed, // Turquoise (default, matches the app icon)
    Color(0xFF3B82F6), // Blue
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Lavender (the original brand hue)
    Color(0xFF06B6D4), // Cyan
    Color(0xFF22C55E), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFFF97316), // Orange
    Color(0xFFEF4444), // Red
    Color(0xFFEC4899), // Pink
    Color(0xFF64748B), // Slate
)
