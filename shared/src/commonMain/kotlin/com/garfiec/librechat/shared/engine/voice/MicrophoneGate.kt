package com.garfiec.librechat.shared.engine.voice

import androidx.compose.runtime.Composable

/** Asks for the microphone before the voice mode joins its room; granted, [onGranted] runs. */
interface MicrophoneGate {
    fun request(onGranted: () -> Unit, onDenied: () -> Unit)
}

@Composable
expect fun rememberMicrophoneGate(): MicrophoneGate
