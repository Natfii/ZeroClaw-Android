/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.remote

import com.zeroclaw.android.model.RemotePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp-backed [PluginRegistryClient] implementation.
 *
 * Performs HTTP GET requests on [Dispatchers.IO] and parses the
 * response JSON via `kotlinx.serialization`.
 *
 * @param client OkHttp client instance (shared for connection pooling).
 */
class OkHttpPluginRegistryClient(
    private val client: OkHttpClient = OkHttpClient(),
) : PluginRegistryClient {
    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("InjectDispatcher")
    override suspend fun fetchPlugins(registryUrl: String): List<RemotePlugin> =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(registryUrl)
                    .get()
                    .build()
            val response = client.newCall(request).execute()
            val body =
                response.use { resp ->
                    check(resp.isSuccessful) {
                        "Registry fetch failed: HTTP ${resp.code}"
                    }
                    resp.body?.string()
                        ?: error("Empty response body from registry")
                }
            json.decodeFromString<List<RemotePlugin>>(body)
        }
}
