/*
 * Copyright 2026 ZeroClaw Community
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
 * both [NetworkCapabilities.NET_CAPABILITY_INTERNET] and
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] is available, and `false`
 * otherwise. The service uses this to pause outbound requests during
 * connectivity gaps and resume when the network returns.
 *
 * @param context Application context for accessing [ConnectivityManager].
 */
class NetworkMonitor(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    private var registered = false

    /** Emits the current network connectivity state. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            /**
             * Called when a network becomes available.
             *
             * Queries the network capabilities to verify both internet
             * access and validation rather than unconditionally reporting
             * connectivity, matching the behavior of [onCapabilitiesChanged].
             */
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                _isConnected.value = caps != null &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                _isConnected.value = checkCurrentConnectivity()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                _isConnected.value =
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

    /**
     * Starts listening for network connectivity changes.
     *
     * Safe to call multiple times; subsequent calls are no-ops when the
     * callback is already registered. Call from [ZeroClawDaemonService.onCreate].
     */
    fun register() {
        if (registered) return
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
    }

    /**
     * Stops listening for network connectivity changes.
     *
     * Safe to call multiple times or when [register] was never called;
     * handles the [IllegalArgumentException] that
     * [ConnectivityManager.unregisterNetworkCallback] throws for an
     * unregistered callback. Call from [ZeroClawDaemonService.onDestroy].
     */
    fun unregister() {
        if (!registered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            /** Callback was already unregistered or never registered. */
        }
        registered = false
    }

    /**
     * Queries the active network for internet and validation capabilities.
     *
     * @return `true` if the active network has both
     *   [NetworkCapabilities.NET_CAPABILITY_INTERNET] and
     *   [NetworkCapabilities.NET_CAPABILITY_VALIDATED]; `false` otherwise.
     */
    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
