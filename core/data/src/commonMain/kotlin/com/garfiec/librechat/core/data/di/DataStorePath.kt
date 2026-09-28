package com.garfiec.librechat.core.data.di

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger

/**
 * The base name of the DataStore preferences file. Kept from LibreChat's time: the engine's
 * addresses and the global profile live in this file, and renaming it would lose them.
 */
internal const val DATASTORE_FILE_NAME = "librechat_settings"

/**
 * Corruption handler for the settings DataStore. A corrupt preferences file (interrupted write,
 * backup-restore) would otherwise throw `CorruptionException` on every read/edit forever — permanently
 * bricking a startup that reads it eagerly. Heal by replacing with empty prefs: the user enters the
 * addresses and signs in again (nothing recoverable is lost; a corrupt file is unreadable anyway).
 */
internal fun settingsCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { e ->
        Logger.e(e) { "Settings DataStore corrupted — replacing with empty preferences" }
        emptyPreferences()
    }
