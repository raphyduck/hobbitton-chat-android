package com.garfiec.librechat.core.data.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.createWithRecovery
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * [AppLockStore] on Android: its own encrypted file (the device keystore's key), holding a salt and
 * a PBKDF2 hash of the code, never the code. A keystore beyond repair leaves the lock in memory for
 * the process: the app then asks for a new code at each start, which is safe rather than open.
 */
class AndroidAppLockStore(context: Context) : AppLockStore {

    private val appContext = context.applicationContext
    private val memory = mutableMapOf<String, Any>()

    private val prefs: SharedPreferences? by lazy {
        createWithRecovery(
            create = {
                EncryptedSharedPreferences.create(
                    appContext,
                    FILE,
                    MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            },
            wipe = { appContext.deleteSharedPreferences(FILE) },
        ).also { if (it == null) Logger.e("Lock") { "Lock store unavailable — kept in memory only" } }
    }

    override fun hasCode(): Boolean = string(KEY_HASH) != null

    override fun setCode(code: String) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        edit {
            putString(KEY_SALT, salt.encode())
            putString(KEY_HASH, hash(code, salt).encode())
        }
    }

    override fun checkCode(code: String): Boolean {
        val salt = string(KEY_SALT)?.decode() ?: return false
        val expected = string(KEY_HASH)?.decode() ?: return false
        return MessageDigest.isEqual(hash(code, salt), expected)
    }

    override fun failures(): Int = prefs?.getInt(KEY_FAILURES, 0) ?: (memory[KEY_FAILURES] as? Int ?: 0)

    override fun setFailures(count: Int) = edit { putInt(KEY_FAILURES, count) }

    override fun biometrics(): Boolean = prefs?.getBoolean(KEY_BIOMETRICS, false) ?: (memory[KEY_BIOMETRICS] as? Boolean ?: false)

    override fun setBiometrics(enabled: Boolean) = edit { putBoolean(KEY_BIOMETRICS, enabled) }

    override fun delayMillis(): Long =
        prefs?.getLong(KEY_DELAY, AppLock.DEFAULT_DELAY_MILLIS) ?: (memory[KEY_DELAY] as? Long ?: AppLock.DEFAULT_DELAY_MILLIS)

    override fun setDelayMillis(millis: Long) = edit { putLong(KEY_DELAY, millis) }

    override fun clear() {
        memory.clear()
        prefs?.edit()?.clear()?.commit()
    }

    private fun string(key: String): String? = prefs?.getString(key, null) ?: memory[key] as? String

    /** `commit`: a failure count that does not reach the disk would let a restart reset it. */
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val store = prefs
        if (store == null) {
            val recorder = MemoryEditor(memory)
            recorder.block()
        } else {
            store.edit().apply(block).commit()
        }
    }

    private fun hash(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val FILE = "eva_lock"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_FAILURES = "failures"
        const val KEY_BIOMETRICS = "biometrics"
        const val KEY_DELAY = "delay_ms"
        const val SALT_BYTES = 16
        const val ALGORITHM = "PBKDF2WithHmacSHA256"

        /** A third of a second on a recent phone: slow for a guesser, unnoticed by the person. */
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}

/** The in-memory fallback's editor: the few calls the store makes, kept in [values]. */
private class MemoryEditor(private val values: MutableMap<String, Any>) : SharedPreferences.Editor {
    override fun putString(key: String, value: String?) = apply { if (value == null) values.remove(key) else values[key] = value }
    override fun putStringSet(key: String, value: MutableSet<String>?) = apply { if (value == null) values.remove(key) else values[key] = value }
    override fun putInt(key: String, value: Int) = apply { values[key] = value }
    override fun putLong(key: String, value: Long) = apply { values[key] = value }
    override fun putFloat(key: String, value: Float) = apply { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
    override fun remove(key: String) = apply { values.remove(key) }
    override fun clear() = apply { values.clear() }
    override fun commit() = true
    override fun apply() = Unit
}
