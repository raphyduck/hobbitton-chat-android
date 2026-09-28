package com.garfiec.librechat.core.data.legacy

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore

// LibreChat's preferences, as it left them in the shared preferences file (D-077): what the
// start-up cleanup removes, and the one value it carries over.

/** Set once LibreChat's leftovers are gone, in the same edit as the purge. */
internal val LEGACY_LIBRECHAT_PURGED: Preferences.Key<Boolean> = booleanPreferencesKey("legacy_librechat_purged")

/** Per-account (`acct:<accountId>:<base>`) and per-server (`srv:<serverId>:<base>`) entries. */
private const val ACCOUNT_PREFIX = "acct:"
private const val SERVER_PREFIX = "srv:"

/** The LibreChat account the app was last signed in to, as the account roster recorded it. */
private const val ACTIVE_ACCOUNT_ID = "active_account_id"

/**
 * LibreChat's global entries: the server URL, the account roster, the chat's settings and caches.
 * Not the language nor the text size, which the app still reads.
 */
private val LIBRECHAT_KEYS = setOf(
    // Server and accounts.
    "server_url",
    ACTIVE_ACCOUNT_ID,
    "account_roster",
    "account_roster_migrated",
    "legacy_claim_done",
    // Caches keyed before accounts existed.
    "cached_startup_config",
    "cached_endpoint_configs",
    "cached_available_models",
    "cached_user_role_permissions",
    "last_used_endpoint",
    "last_used_model",
    // The chat's settings.
    "latex_renderer",
    "starred_models_display",
    "chat_header_content",
    "chat_header_alignment",
    "auto_scroll_enabled",
    "prefetch_enabled",
    "prefetch_attachments",
    "prefetch_on_metered",
    "prefetch_depth",
    "show_thinking_blocks",
    "context_bar_placement",
    "during_run_action",
    "upload_routing_mode",
    "context_gauge_expanded",
    "auto_read_enabled",
    "show_image_descriptions",
    "selected_voice_id",
    "dismiss_keyboard_on_send",
    "tts_source",
    "tts_speech_rate",
    "tts_pitch",
    "tts_voice_name",
    "tablet_sidebar_open",
    "tablet_sidebar_gesture_enabled",
    "auto_send_after_stt",
    "stt_engine",
    "stt_language",
    "stt_on_device",
    "stt_end_of_speech",
    "tts_engine",
    "tts_voice",
    "tts_caching",
    "chat_layout_style",
    "files_view_mode",
    "files_sort_field",
    "files_sort_order",
    "show_avatars",
    "show_bubbles",
    "inline_artifact_mermaid",
    "inline_artifact_svg",
    "inline_artifact_html",
    "inline_artifact_react",
    "inline_artifact_markdown",
    "artifact_display_mode",
    "dismissed_version_warning",
    "selected_mcp_servers",
    "enabled_tools",
)

/**
 * Moves the global profile a LibreChat account held (`acct:<id>:chat_profile_*`) under the device
 * keys [GlobalProfileStore] reads, unless the device already has one of its own. The account is
 * the one last signed in; failing that, the only account that has a profile. Nothing to move, or
 * no way to tell which account's, leaves the device's profile as it is.
 */
internal fun MutablePreferences.adoptAccountProfile() {
    if (GlobalProfileStore.BASES.any { this[GlobalProfileStore.deviceKey(it)] != null }) return
    val entries = asMap().mapKeys { it.key.name }
    val withProfile = entries.keys
        .filter { name -> name.startsWith(ACCOUNT_PREFIX) && GlobalProfileStore.BASES.any { name.endsWith(":$it") } }
        .map { name -> name.removePrefix(ACCOUNT_PREFIX).substringBeforeLast(':') }
        .toSet()
    val active = entries[ACTIVE_ACCOUNT_ID] as? String
    val accountId = active?.takeIf { it in withProfile } ?: withProfile.singleOrNull() ?: return
    GlobalProfileStore.BASES.forEach { base ->
        (entries["$ACCOUNT_PREFIX$accountId:$base"] as? String)?.let { this[GlobalProfileStore.deviceKey(base)] = it }
    }
}

/** Removes every LibreChat entry: [LIBRECHAT_KEYS], and all per-account and per-server ones. */
internal fun MutablePreferences.purgeLibreChatEntries() {
    asMap().keys
        .filter { key ->
            key.name in LIBRECHAT_KEYS || key.name.startsWith(ACCOUNT_PREFIX) || key.name.startsWith(SERVER_PREFIX)
        }
        .toList()
        .forEach { remove(it) }
}
