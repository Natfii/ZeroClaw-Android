/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.remote

import com.zeroclaw.android.model.RemotePlugin

/**
 * Client interface for fetching plugin metadata from a remote registry.
 */
interface PluginRegistryClient {
    /**
     * Fetches the list of available plugins from the given registry URL.
     *
     * Safe to call from any thread. Implementations must handle their
     * own dispatcher switching.
     *
     * @param registryUrl URL of the plugin registry JSON endpoint.
     * @return List of [RemotePlugin] metadata, or throws on network/parse errors.
     * @throws java.io.IOException On network failure.
     * @throws kotlinx.serialization.SerializationException On JSON parse failure.
     */
    suspend fun fetchPlugins(registryUrl: String): List<RemotePlugin>
}
