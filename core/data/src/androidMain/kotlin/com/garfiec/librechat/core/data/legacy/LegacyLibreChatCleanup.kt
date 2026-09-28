package com.garfiec.librechat.core.data.legacy

import android.app.job.JobScheduler
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Removes, once, what LibreChat left on a device that ran an earlier build (D-077).
 *
 * LibreChat is gone from the server and from the app, but an install that knew it still carries
 * its data: the Room cache of conversations and messages (`librechat.db`), its tokens
 * (`librechat_tokens`, EncryptedSharedPreferences), the background prefetch's WorkManager job and
 * bookkeeping, a few cache directories, and its preferences — the account roster, the server URL,
 * the per-account and per-server entries, the chat settings.
 *
 * What stays is named, not guessed: the preferences below are removed **by name**, so the engine's
 * own (its addresses, which session is a chat, where each transcript was left), the theme, the
 * language, the text size and the global profile are never touched. The global profile is the one
 * LibreChat value carried over: an install that kept it under its LibreChat account has it moved
 * under the device keys ([adoptAccountProfile]) before the account entries go.
 *
 * Once done, a flag in the same preferences says so, written in the same edit as the purge.
 */
class LegacyLibreChatCleanup(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val appScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Runs the cleanup off the main thread, at most once per install. Never throws. */
    fun launchOnce() {
        appScope.launch(ioDispatcher) {
            try {
                runOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Could not remove LibreChat's leftovers; will try again at next start" }
            }
        }
    }

    internal suspend fun runOnce() {
        if (dataStore.data.first()[LEGACY_LIBRECHAT_PURGED] == true) return
        removeFiles()
        dataStore.edit { prefs ->
            prefs.adoptAccountProfile()
            prefs.purgeLibreChatEntries()
            prefs[LEGACY_LIBRECHAT_PURGED] = true
        }
        Logger.i { "LibreChat's leftovers removed from this device" }
    }

    private fun removeFiles() {
        // deleteDatabase takes the journal, WAL and SHM companions with it.
        context.deleteDatabase(LIBRECHAT_DATABASE)
        // The prefetch was LibreChat's only WorkManager job, and WorkManager has left the app: its
        // pending job is cancelled at the system scheduler, and its own database goes too.
        context.getSystemService(JobScheduler::class.java)?.cancelAll()
        context.deleteDatabase(WORK_MANAGER_DATABASE)
        PREFERENCE_FILES.forEach { context.deleteSharedPreferences(it) }
        CACHE_DIRECTORIES.forEach { File(context.cacheDir, it).deleteRecursively() }
    }

    private companion object {
        const val LIBRECHAT_DATABASE = "librechat.db"
        const val WORK_MANAGER_DATABASE = "androidx.work.workdb"

        /** LibreChat's tokens, and the prefetch's record of how its job was registered. */
        val PREFERENCE_FILES = listOf("librechat_tokens", "prefetch_schedule")

        /**
         * LibreChat's own cache directories. Not the image cache (the app's image loader keeps it)
         * nor the dictation's (`mission_dictation`), which the engine's composer writes.
         */
        val CACHE_DIRECTORIES = listOf(
            "artifacts",
            "shared_images",
            "shared_files",
            "camera_photos",
            "pdf_preview",
            "voice_recording",
            "audio",
            "tts",
            "voice_test",
        )
    }
}
