package com.garfiec.librechat.core.common.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidConnectivityObserver(
    private val context: Context,
) : ConnectivityObserver {
    // Permission is declared in app module's AndroidManifest.xml
    @SuppressLint("MissingPermission")
    override val isConnected: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        // Emit current state
        val currentNetwork = connectivityManager.activeNetwork
        val currentCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(currentCapabilities.hasInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.hasInternet())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * The phone's DEFAULT network changing: another one takes over, or it is lost. The default one
     * only, and never the one found at registration (26/10/2026, the day's regression: registered
     * on every network with Internet, this fired for the mobile network too, which comes and goes
     * every few seconds while Wi-Fi is up, and each time the app dropped its connections and the
     * transcript being read with them: a conversation stayed on its placeholders two minutes).
     */
    @SuppressLint("MissingPermission")
    override val networkChanges: Flow<Unit> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        var current: Network? = null
        var seenOne = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val changed = seenOne && network != current
                current = network
                seenOne = true
                if (changed) trySend(Unit)
            }

            override fun onLost(network: Network) {
                if (network == current) {
                    current = null
                    trySend(Unit)
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun NetworkCapabilities?.hasInternet(): Boolean {
        return this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
