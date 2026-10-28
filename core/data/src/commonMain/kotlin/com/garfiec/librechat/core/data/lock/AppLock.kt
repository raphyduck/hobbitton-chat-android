package com.garfiec.librechat.core.data.lock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Where the lock keeps its code and settings: the platform's encrypted storage, the code itself
 * never kept, only a slow salted hash of it. Blocking calls; [AppLock] runs them off the main thread.
 */
interface AppLockStore {
    fun hasCode(): Boolean
    fun setCode(code: String)
    fun checkCode(code: String): Boolean
    fun failures(): Int
    fun setFailures(count: Int)
    fun biometrics(): Boolean
    fun setBiometrics(enabled: Boolean)
    fun delayMillis(): Long
    fun setDelayMillis(millis: Long)

    /** Forgets everything: the code, the failures, the biometrics' word. */
    fun clear()
}

/** What the gate shows over the signed-in app. */
enum class LockState {
    /** Still reading the store: nothing but the background. */
    CHECKING,

    /** No code yet: one is created before anything else is shown. */
    UNSET,
    LOCKED,
    UNLOCKED,
}

sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data class Wrong(val attemptsLeft: Int) : UnlockResult

    /** The last attempt failed: the code is forgotten, the app stays locked, the caller signs out. */
    data object TooManyAttempts : UnlockResult
}

/**
 * The app's own lock (28/10/2026, Raphaël: « au vu de l'accès que donne l'application Eva, il
 * faudrait sécuriser par un mot de passe et optionnellement de la biométrie, comme une application
 * bancaire »). A six-digit code, created right after the sign-in and asked at every start and on
 * every return after [delay][AppLockStore.delayMillis] in the background; the fingerprint, when
 * turned on, stands in for it. [MAX_ATTEMPTS] wrong codes in a row sign out: the next person has
 * to go through the portal, its password and its second factor.
 *
 * [clock] is a monotonic clock (the device's uptime): moving the phone's date does not open it.
 */
class AppLock(
    private val store: AppLockStore,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val _biometrics = MutableStateFlow(false)

    /** Whether the fingerprint may unlock; the screen still asks the phone whether it can. */
    val biometrics: StateFlow<Boolean> = _biometrics.asStateFlow()

    private val _delayMillis = MutableStateFlow(DEFAULT_DELAY_MILLIS)
    val delayMillis: StateFlow<Long> = _delayMillis.asStateFlow()

    private var leftAt: Long? = null

    /** Reads the store once, at start: a code set locks the app, none asks for one. */
    suspend fun start() {
        if (_state.value != LockState.CHECKING) return
        val (hasCode, biometrics, delay) = withContext(io) { Triple(store.hasCode(), store.biometrics(), store.delayMillis()) }
        _biometrics.value = hasCode && biometrics
        _delayMillis.value = delay
        _state.value = if (hasCode) LockState.LOCKED else LockState.UNSET
    }

    /** The app left the screen: the delay starts. */
    fun onBackground() {
        if (_state.value == LockState.UNLOCKED) leftAt = clock()
    }

    /** The app is back: locked again once it stayed away for the delay. */
    fun onForeground() {
        val left = leftAt ?: return
        leftAt = null
        if (_state.value == LockState.UNLOCKED && clock() - left >= _delayMillis.value) {
            _state.value = LockState.LOCKED
        }
    }

    suspend fun create(code: String) {
        require(isValidCode(code)) { "a code is $CODE_LENGTH digits" }
        withContext(io) {
            store.setCode(code)
            store.setFailures(0)
        }
        _state.value = LockState.UNLOCKED
    }

    suspend fun unlock(code: String): UnlockResult {
        val right = withContext(io) { store.checkCode(code) }
        if (right) {
            withContext(io) { store.setFailures(0) }
            _state.value = LockState.UNLOCKED
            return UnlockResult.Unlocked
        }
        val failures = withContext(io) { (store.failures() + 1).also(store::setFailures) }
        if (failures >= MAX_ATTEMPTS) {
            // The code is gone but the app stays locked: no code opens it any more, and no new
            // one can be set before the sign-out the caller runs ends in [forget].
            withContext(io) { store.clear() }
            _biometrics.value = false
            return UnlockResult.TooManyAttempts
        }
        return UnlockResult.Wrong(MAX_ATTEMPTS - failures)
    }

    /** The phone confirmed the fingerprint (a strong biometric, its key still valid). */
    suspend fun unlockWithBiometrics() {
        if (!_biometrics.value) return
        withContext(io) { store.setFailures(0) }
        _state.value = LockState.UNLOCKED
    }

    /** Checks the current code without touching the lock: changing the code asks for it first. */
    suspend fun check(code: String): Boolean = withContext(io) { store.checkCode(code) }

    suspend fun changeCode(code: String) {
        require(isValidCode(code)) { "a code is $CODE_LENGTH digits" }
        withContext(io) { store.setCode(code) }
    }

    suspend fun setBiometrics(enabled: Boolean) {
        withContext(io) { store.setBiometrics(enabled) }
        _biometrics.value = enabled
    }

    suspend fun setDelay(millis: Long) {
        withContext(io) { store.setDelayMillis(millis) }
        _delayMillis.value = millis
    }

    /** Signing out forgets the lock: the next person signs in and sets their own code. */
    suspend fun forget() {
        withContext(io) { store.clear() }
        _biometrics.value = false
        _delayMillis.value = DEFAULT_DELAY_MILLIS
        leftAt = null
        _state.value = LockState.UNSET
    }

    companion object {
        const val CODE_LENGTH = 6
        const val MAX_ATTEMPTS = 5
        const val DEFAULT_DELAY_MILLIS = 60_000L

        /** The choices of Settings: at once, one minute, five, fifteen. */
        val DELAYS_MILLIS = listOf(0L, 60_000L, 300_000L, 900_000L)

        fun isValidCode(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in '0'..'9' }

        /** One digit six times, or a run up or down (« 123456 », « 987654 »): the first guesses. */
        fun isTooSimple(code: String): Boolean {
            if (code.length < 2) return true
            val steps = code.zipWithNext { a, b -> b - a }.toSet()
            return steps.size == 1 && steps.single() in -1..1
        }
    }
}
