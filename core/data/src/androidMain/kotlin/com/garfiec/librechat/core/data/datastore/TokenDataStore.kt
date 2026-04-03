package com.garfiec.librechat.core.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.ktor.client.HttpClient
import java.security.KeyStoreException

class TokenDataStore(
    context: Context,
    refreshClient: Lazy<HttpClient>,
) : CommonTokenDataStore(refreshClient) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init {
        initializeTokenCache()
    }

    override fun readAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun readRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun writeTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun removeTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    override fun onKeystoreCorruption() {
        removeTokens()
    }

    override fun isKeystoreException(e: Exception): Boolean = e is KeyStoreException

    companion object {
        private const val PREFS_NAME = "librechat_tokens"
    }
}
