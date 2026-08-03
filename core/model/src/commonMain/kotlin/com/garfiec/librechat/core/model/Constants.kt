package com.garfiec.librechat.core.model

const val SAVED_TAG = "Saved"

/**
 * `conversation_id` sentinel for the new-chat compose-box draft, which has no owning conversation yet.
 * Canonical here in `:core:model` (a shared, non-entity location) so the runtime key, the drafts entity,
 * and the legacy claim's special-case all reference one constant and can never drift. The legacy claim
 * treats this single conv-less row specially so an upgrading user's unsent text survives the sweep
 * (see AccountClaimDao.claimLegacyRows).
 */
const val NEW_CHAT_DRAFT_KEY: String = "__new_chat__"

/**
 * Default accent seed color as `0xAARRGGBB` — the turquoise brand hue of the app icon's cable,
 * sampled from the iOS 1024px icon asset. Canonical here in `:core:model` (the shared, pure-Kotlin
 * module both consumers depend on) so the Compose seed (`DefaultAccentSeed` in `:core:ui`) and the
 * persisted-preference fallback (`ThemeDataStore.DEFAULT_ACCENT_COLOR` in `:core:data`) derive from
 * one literal and can never drift.
 */
const val DEFAULT_ACCENT_SEED_ARGB: Long = 0xFF00D8BB
