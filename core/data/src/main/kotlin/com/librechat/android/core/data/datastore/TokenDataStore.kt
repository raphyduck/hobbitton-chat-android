package com.librechat.android.core.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.librechat.android.core.model.response.RefreshResponse
import com.librechat.android.core.network.client.CookieHelper
import com.librechat.android.core.network.client.SecureTokenStorage
import com.librechat.android.core.network.client.TokenManager
import com.librechat.android.core.network.di.RefreshClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import java.io.IOException
import java.security.KeyStoreException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext context: Context,
    @RefreshClient private val refreshClient: dagger.Lazy<HttpClient>,
) : TokenManager, SecureTokenStorage {

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

    /** Volatile in-memory cache for fast access-token reads. */
    @Volatile
    private var cachedAccessToken: String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    private val refreshMutex = Mutex()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // --- TokenManager ---

    override suspend fun getAccessToken(): String? = cachedAccessToken

    override suspend fun setTokens(accessToken: String, refreshToken: String) {
        cachedAccessToken = accessToken
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        // Re-check cached token: another coroutine may have already refreshed while we waited
        // on the mutex. If the cached token differs from what triggered the 401, skip refresh.
        val storedRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (storedRefreshToken.isNullOrBlank()) {
            Timber.w("No refresh token available")
            return false
        }

        return try {
            // Send refresh token via both Cookie header and request body.
            // The LibreChat backend reads from req.cookies.refreshToken first,
            // falling back to req.body.refreshToken. Sending both ensures
            // compatibility across backend versions.
            val httpResponse: HttpResponse = refreshClient.get()
                .post("/api/auth/refresh") {
                    header("Cookie", "refreshToken=$storedRefreshToken")
                    setBody(mapOf("refreshToken" to storedRefreshToken))
                }

            val body: RefreshResponse = httpResponse.body()

            // The backend may rotate the refresh token via Set-Cookie.
            // If a new refresh token is present, persist it; otherwise keep the current one.
            val newRefreshToken = CookieHelper.extractRefreshToken(httpResponse.headers)
                ?: storedRefreshToken

            setTokens(body.token, newRefreshToken)
            Timber.d("Token refreshed successfully")
            true
        } catch (e: KeyStoreException) {
            Timber.e(e, "Keystore corruption—clearing tokens")
            clearTokens()
            false
        } catch (e: IOException) {
            Timber.w(e, "Network error during token refresh")
            false
        } catch (e: ClientRequestException) {
            // 401/403 means session is truly expired
            Timber.w(e, "Auth error during token refresh (status=${e.response.status})")
            clearTokens()
            false
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during token refresh")
            false
        }
    }

    override suspend fun clearTokens() {
        cachedAccessToken = null
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    override fun emitSessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }

    // --- SecureTokenStorage ---

    override suspend fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH_TOKEN, null)

    override suspend fun storeTokens(accessToken: String, refreshToken: String) {
        setTokens(accessToken, refreshToken)
    }

    override suspend fun clearAll() {
        clearTokens()
    }

    companion object {
        private const val PREFS_NAME = "librechat_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
