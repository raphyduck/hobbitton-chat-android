package com.garfiec.librechat.shared.engine.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberMicrophoneGate(): MicrophoneGate {
    val context = LocalContext.current
    val pending = remember { Pending() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callbacks = pending.take()
        if (granted) callbacks?.first?.invoke() else callbacks?.second?.invoke()
    }
    return remember(context, launcher) {
        object : MicrophoneGate {
            override fun request(onGranted: () -> Unit, onDenied: () -> Unit) {
                val held = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (held) {
                    onGranted()
                } else {
                    pending.set(onGranted, onDenied)
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

private class Pending {
    private var callbacks: Pair<() -> Unit, () -> Unit>? = null
    fun set(onGranted: () -> Unit, onDenied: () -> Unit) {
        callbacks = onGranted to onDenied
    }
    fun take(): Pair<() -> Unit, () -> Unit>? = callbacks.also { callbacks = null }
}
