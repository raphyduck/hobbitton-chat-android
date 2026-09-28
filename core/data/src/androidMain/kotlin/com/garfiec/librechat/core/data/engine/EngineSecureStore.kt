package com.garfiec.librechat.core.data.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.createWithRecovery
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The portal's tokens on disk — and nothing else since D-076.
 *
 * In its own encrypted file, not LibreChat's. Two authorities, two lifetimes: a chat session
 * expiring must not take the portal's refresh token with it, and the reverse. An explicit sign-out
 * purges both, deliberately (`PortalSignOut`).
 *
 * The engine's Basic password used to live here too, typed into the settings and sent on every
 * request. The edge presents it now, and the app must not keep a copy: the first time this store
 * is opened, a password left by an older build is deleted ([LEGACY_PASSWORD]).
 *
 * If the device keystore is beyond repair, [createWithRecovery] returns null and everything lives
 * in memory until the process dies: the person re-visits the portal, which is a bad afternoon
 * rather than a crash loop at startup.
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
            } else if (it.contains(LEGACY_PASSWORD)) {
                // `commit`, not `apply`: this is the one write whose whole point is that the value
                // is gone from disk, and the lazy block already runs on the IO dispatcher.
                it.edit().remove(LEGACY_PASSWORD).commit()
                Logger.i("Engine") { "Removed the engine password an earlier build had stored (D-076)" }
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
        }
    }

    private companion object {
        const val FILE = "engine_secrets"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"

        /** Where builds before D-076 kept the engine's Basic password. Only ever deleted now. */
        const val LEGACY_PASSWORD = "basic_password"
    }
}
