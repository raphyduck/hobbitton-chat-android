package com.garfiec.librechat.core.model

/**
 * Default accent seed color as `0xAARRGGBB`. Canonical here in `:core:model` (the shared,
 * pure-Kotlin module both consumers depend on) so the Compose seed (`DefaultAccentSeed` in
 * `:core:ui`) and the persisted-preference fallback (`ThemeDataStore.DEFAULT_ACCENT_COLOR` in
 * `:core:data`) derive from one literal and can never drift.
 *
 * Only this color's **hue** reaches the generated scheme — see `LibreChatTheme` for why. Its
 * chroma and tone are inert there, so judge a replacement by the roles it generates, never by
 * how the hex reads as a swatch.
 */
const val DEFAULT_ACCENT_SEED_ARGB: Long = 0xFF3B82F6
