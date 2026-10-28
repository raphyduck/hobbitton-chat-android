package com.garfiec.librechat.shared.engine.lock

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.lock.AppLock
import com.garfiec.librechat.core.data.lock.LockState
import com.garfiec.librechat.core.data.lock.UnlockResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppLockUiState(
    val lock: LockState = LockState.CHECKING,
    /** The fingerprint is turned on (the screen still asks the phone whether it can). */
    val biometrics: Boolean = false,
    val delayMillis: Long = AppLock.DEFAULT_DELAY_MILLIS,
)

/**
 * The lock's screens over [AppLock]: the gate over the signed-in app, and the Settings section.
 * Null without the engine graph, where nothing is signed in to lock.
 */
class AppLockViewModel(private val lock: AppLock?) : ViewModel() {

    val state: StateFlow<AppLockUiState> = if (lock == null) {
        MutableStateFlow(AppLockUiState(lock = LockState.UNLOCKED))
    } else {
        combine(lock.state, lock.biometrics, lock.delayMillis, ::AppLockUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockUiState())
    }

    init {
        lock?.let { viewModelScope.launch { it.start() } }
    }

    fun onBackground() {
        lock?.onBackground()
    }

    fun onForeground() {
        lock?.onForeground()
    }

    /** Sets the first code; [biometrics] when the fingerprint was confirmed right after it. */
    fun create(code: String, biometrics: Boolean) {
        val target = lock ?: return
        viewModelScope.launch {
            target.create(code)
            if (biometrics) target.setBiometrics(true)
        }
    }

    fun unlock(code: String, onResult: (UnlockResult) -> Unit) {
        val target = lock ?: return
        viewModelScope.launch { onResult(target.unlock(code)) }
    }

    fun unlockWithBiometrics() {
        val target = lock ?: return
        viewModelScope.launch { target.unlockWithBiometrics() }
    }

    fun check(code: String, onResult: (Boolean) -> Unit) {
        val target = lock ?: return
        viewModelScope.launch { onResult(target.check(code)) }
    }

    fun changeCode(code: String) {
        val target = lock ?: return
        viewModelScope.launch { target.changeCode(code) }
    }

    fun setBiometrics(enabled: Boolean) {
        val target = lock ?: return
        viewModelScope.launch { target.setBiometrics(enabled) }
    }

    fun setDelay(millis: Long) {
        val target = lock ?: return
        viewModelScope.launch { target.setDelay(millis) }
    }
}
