/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network connectivity changes via
 * [ConnectivityManager.NetworkCallback].
 *
 * Exposes an [isConnected] flow that emits `true` when any network with
 * internet capability is available and `false` otherwise. The service
 * uses this to pause outbound requests during connectivity gaps and
 * resume when the network returns.
 *
 * @param context Application context for accessing [ConnectivityManager].
 */
class NetworkMonitor(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())

    /** Emits the current network connectivity state. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
            }

            override fun onLost(network: Network) {
                _isConnected.value = checkCurrentConnectivity()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                _isConnected.value =
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET,
                    )
            }
        }

    /**
     * Starts listening for network connectivity changes.
     *
     * Call from [ZeroClawDaemonService.onCreate].
     */
    fun register() {
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Stops listening for network connectivity changes.
     *
     * Call from [ZeroClawDaemonService.onDestroy].
     */
    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
        )
    }
}
