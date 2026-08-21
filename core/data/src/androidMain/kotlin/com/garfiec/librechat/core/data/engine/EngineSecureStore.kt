package com.garfiec.librechat.core.data.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.createWithRecovery
import com.garfiec.librechat.core.network.engine.EnginePasswordStore
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The engine's secrets on disk: the Basic password the person configured, and the portal's tokens.
 *
 * In its own encrypted file, not LibreChat's. Two authorities, two lifetimes — sharing one would
 * mean a chat logout wiping the engine's refresh token, and the engine's password surviving a
 * « forget this server ».
 *
 * The two interfaces are exposed as [tokens] and [password] rather than implemented by this class
 * directly: `read()`, `write()` and `clear()` collide by name, and forcing them onto one type would
 * mean renaming the operations for a reason that has nothing to do with what they do.
 *
 * If the device keystore is beyond repair, [createWithRecovery] returns null and everything lives
 * in memory until the process dies: the person re-enters the password and re-visits the portal,
 * which is a bad afternoon rather than a crash loop at startup.
 */
class EngineSecureStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val appContext = context.applicationContext
    private val memory = mutableMapOf<String, String>()

    private val prefs: SharedPreferences? by lazy {
        createWithRecovery(
            create = {
                EncryptedSharedPreferences.create(
                    appContext,
                    FILE,
                    MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            },
            wipe = { appContext.deleteSharedPreferences(FILE) },
        ).also {
            if (it == null) {
                Logger.e("Engine") { "Engine secure store unavailable — session kept in memory only" }
            }
        }
    }

    private suspend fun put(key: String, value: String?) = withContext(ioDispatcher) {
        val store = prefs
        if (store == null) {
            if (value == null) memory.remove(key) else memory[key] = value
        } else {
            store.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }

    private suspend fun get(key: String): String? = withContext(ioDispatcher) {
        prefs?.getString(key, null) ?: memory[key]
    }

    val tokens: EngineTokenStore = object : EngineTokenStore {
        override suspend fun read(): EngineTokens? {
            val access = get(KEY_ACCESS) ?: return null
            return EngineTokens(
                accessToken = access,
                refreshToken = get(KEY_REFRESH),
                // Absent reads as « no known expiry », which the session manager takes at face
                // value rather than as expired — renewing on every request otherwise.
                expiresAtEpochSeconds = get(KEY_EXPIRES)?.toLongOrNull(),
            )
        }

        override suspend fun write(tokens: EngineTokens) {
            put(KEY_ACCESS, tokens.accessToken)
            put(KEY_REFRESH, tokens.refreshToken)
            put(KEY_EXPIRES, tokens.expiresAtEpochSeconds?.toString())
        }

        override suspend fun clear() {
            put(KEY_ACCESS, null)
            put(KEY_REFRESH, null)
            put(KEY_EXPIRES, null)
            // Deliberately NOT the password: ending a session must not undo the configuration.
        }
    }

    val password: EnginePasswordStore = object : EnginePasswordStore {
        override suspend fun read(): String? = get(KEY_PASSWORD)
        override suspend fun write(password: String) {
            put(KEY_PASSWORD, password)
        }

        override suspend fun clear() {
            put(KEY_PASSWORD, null)
        }
    }

    private companion object {
        const val FILE = "engine_secrets"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_PASSWORD = "basic_password"
    }
}
