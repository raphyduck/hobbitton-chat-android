package com.garfiec.librechat.shared.engine.lock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import co.touchlab.kermit.Logger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@Composable
actual fun rememberBiometrics(): Biometrics? {
    val activity = LocalActivity.current as? FragmentActivity ?: return null
    return remember(activity) { AndroidBiometrics(activity) }
}

/**
 * The fingerprint over [BiometricPrompt] with a crypto object: the prompt unlocks an AES key that
 * needs a strong biometric for each use and that Android voids when the enrolled fingerprints
 * change. A success is only counted once that key has encrypted something.
 */
private class AndroidBiometrics(private val activity: FragmentActivity) : Biometrics {

    override val available: Boolean
        get() = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun enroll(): Boolean = runCatching {
        forget()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
        true
    }.getOrElse {
        Logger.w(it) { "Lock: the fingerprint key could not be created" }
        false
    }

    override fun authenticate(title: String, cancel: String, onResult: (BiometricOutcome) -> Unit) {
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return onResult(BiometricOutcome.INVALIDATED)
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            Logger.i { "Lock: the phone's fingerprints changed, the fingerprint is off" }
            forget()
            return onResult(BiometricOutcome.INVALIDATED)
        } catch (e: Exception) {
            Logger.w(e) { "Lock: the fingerprint key is unusable" }
            return onResult(BiometricOutcome.INVALIDATED)
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val proven = runCatching { result.cryptoObject?.cipher?.doFinal(PROBE) }.getOrNull() != null
                    onResult(if (proven) BiometricOutcome.SUCCESS else BiometricOutcome.CANCELLED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(BiometricOutcome.CANCELLED)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    override fun forget() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "eva_lock_fingerprint"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val PROBE = byteArrayOf(1)
    }
}
