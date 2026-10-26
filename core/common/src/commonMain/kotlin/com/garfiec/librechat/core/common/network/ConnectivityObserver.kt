package com.garfiec.librechat.core.common.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

interface ConnectivityObserver {
    val isConnected: Flow<Boolean>

    /**
     * One emission each time the phone's network changes: a network gained or lost, Wi-Fi to
     * mobile and back. Connections opened before are suspect from then on (26/10/2026: the engine's
     * HTTP/2 connection survived a change of network as a dead socket, and every request queued on
     * it). By default, the connected state's changes.
     */
    val networkChanges: Flow<Unit>
        get() = isConnected.drop(1).map { }
}
