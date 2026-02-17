/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import com.zeroclaw.android.model.DiscoveredServer
import com.zeroclaw.android.model.LocalServerType
import com.zeroclaw.android.model.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scans the local network for AI inference servers.
 *
 * Probes common AI server ports across the local /24 subnet using TCP
 * connection attempts, then identifies server types via HTTP probes and
 * optionally polls loaded models. Results are emitted as [ScanState]
 * updates via a [Flow].
 *
 * The scan is battery-conscious: it limits concurrent connections with
 * a [Semaphore], uses short timeouts, and runs entirely on [Dispatchers.IO].
 */
@Suppress("TooManyFunctions")
object NetworkScanner {
    /** Common AI server ports to probe, ordered by popularity. */
    private val TARGET_PORTS = intArrayOf(
        PORT_OLLAMA,
        PORT_LM_STUDIO,
        PORT_VLLM,
        PORT_LOCALAI,
    )

    /** Maximum concurrent TCP connection attempts. */
    private const val MAX_CONCURRENT = 64

    /** TCP connection timeout per host:port in milliseconds. */
    private const val CONNECT_TIMEOUT_MS = 400

    /** HTTP read timeout for identification probes in milliseconds. */
    private const val HTTP_TIMEOUT_MS = 3000

    /** Total number of hosts in a /24 subnet (excluding network and broadcast). */
    private const val SUBNET_HOST_COUNT = 254

    /**
     * Scans the local network and emits [ScanState] updates.
     *
     * The flow emits [ScanState.Scanning] with progress updates, then
     * [ScanState.Completed] with the list of all discovered servers, or
     * [ScanState.Error] if the scan cannot start (e.g. no local network).
     *
     * @param context Application context for accessing [ConnectivityManager].
     * @return A cold [Flow] of scan state updates.
     */
    @Suppress("InjectDispatcher")
    fun scan(context: Context): Flow<ScanState> = channelFlow {
        val subnet = getLocalSubnet(context)
        if (subnet == null) {
            send(ScanState.Error("Not connected to a local network"))
            return@channelFlow
        }

        send(ScanState.Scanning(0f))

        val totalProbes = SUBNET_HOST_COUNT * TARGET_PORTS.size
        val completed = AtomicInteger(0)

        val servers = coroutineScope {
            val jobs = launchProbeJobs(subnet, completed)

            val progressJob = async {
                emitProgress(completed, totalProbes)
            }

            val results = jobs.awaitAll().filterNotNull()
            progressJob.cancel()
            results
        }

        send(ScanState.Completed(servers))
    }.flowOn(Dispatchers.IO)

    /**
     * Launches parallel probe jobs for every host:port combination in the subnet.
     *
     * @param subnet The /24 subnet prefix (e.g. "192.168.1").
     * @param completed Atomic counter incremented after each probe completes.
     * @return List of deferred probe results.
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.launchProbeJobs(
        subnet: String,
        completed: AtomicInteger,
    ): List<kotlinx.coroutines.Deferred<DiscoveredServer?>> {
        val semaphore = Semaphore(MAX_CONCURRENT)
        return buildList {
            for (host in 1..SUBNET_HOST_COUNT) {
                val ip = "$subnet.$host"
                for (port in TARGET_PORTS) {
                    add(
                        async {
                            try {
                                semaphore.withPermit { probeHost(ip, port) }
                            } finally {
                                completed.incrementAndGet()
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * Emits scanning progress at regular intervals until all probes complete.
     *
     * @param completed Atomic counter of completed probes.
     * @param total Total number of probes to run.
     */
    private suspend fun ProducerScope<ScanState>.emitProgress(
        completed: AtomicInteger,
        total: Int,
    ) {
        while (completed.get() < total) {
            send(ScanState.Scanning(completed.get().toFloat() / total))
            kotlinx.coroutines.delay(PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    /**
     * Extracts the /24 subnet prefix from the device's active network interface.
     *
     * @param context Application context for [ConnectivityManager] access.
     * @return Subnet prefix (e.g. "192.168.1") or null if unavailable.
     */
    private fun getLocalSubnet(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProps = cm.getLinkProperties(network) ?: return null

        val ipv4 = linkProps.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
            ?: return null

        val parts = ipv4.split(".")
        return if (parts.size == OCTET_COUNT) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            null
        }
    }

    /**
     * Attempts a TCP connection followed by server identification on success.
     *
     * @param ip Target IP address.
     * @param port Target port.
     * @return A [DiscoveredServer] if an AI server is detected, null otherwise.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun probeHost(ip: String, port: Int): DiscoveredServer? {
        if (!isPortOpen(ip, port)) return null
        return identifyServer(ip, port)
    }

    /**
     * Tests whether a TCP port is reachable at the given address.
     *
     * @param ip Target IP address.
     * @param port Target port number.
     * @return True if the connection succeeds within the timeout.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun isPortOpen(ip: String, port: Int): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }

    /**
     * Identifies the type of AI server and polls loaded models.
     *
     * Tries Ollama's `/api/tags` first (if on the default Ollama port),
     * then falls back to the OpenAI-compatible `/v1/models` endpoint.
     *
     * @param ip Server IP address.
     * @param port Server port number.
     * @return A [DiscoveredServer] with type and models, or null if unidentified.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun identifyServer(ip: String, port: Int): DiscoveredServer? {
        val ollamaResult = tryOllama(ip, port)
        if (ollamaResult != null) return ollamaResult

        val openaiResult = tryOpenAiCompatible(ip, port)
        if (openaiResult != null) return openaiResult

        return null
    }

    /**
     * Probes for an Ollama server and extracts loaded models.
     *
     * @param ip Server IP address.
     * @param port Server port number.
     * @return A [DiscoveredServer] if Ollama is detected, null otherwise.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun tryOllama(ip: String, port: Int): DiscoveredServer? =
        try {
            val json = httpGet("http://$ip:$port/api/tags")
            val root = JSONObject(json)
            val modelsArray = root.optJSONArray("models") ?: return null
            val models = buildList {
                for (i in 0 until modelsArray.length()) {
                    val name = modelsArray.getJSONObject(i).optString("name", "")
                    if (name.isNotEmpty()) add(name)
                }
            }
            DiscoveredServer(ip, port, LocalServerType.OLLAMA, models)
        } catch (e: Exception) {
            null
        }

    /**
     * Probes for an OpenAI-compatible server and extracts model IDs.
     *
     * @param ip Server IP address.
     * @param port Server port number.
     * @return A [DiscoveredServer] if an OpenAI-compatible API is detected, null otherwise.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun tryOpenAiCompatible(ip: String, port: Int): DiscoveredServer? =
        try {
            val json = httpGet("http://$ip:$port/v1/models")
            val root = JSONObject(json)
            val dataArray = root.optJSONArray("data") ?: return null
            val models = buildList {
                for (i in 0 until dataArray.length()) {
                    val id = dataArray.getJSONObject(i).optString("id", "")
                    if (id.isNotEmpty()) add(id)
                }
            }
            DiscoveredServer(ip, port, LocalServerType.OPENAI_COMPATIBLE, models)
        } catch (e: Exception) {
            null
        }

    /**
     * Performs a simple HTTP GET and returns the response body.
     *
     * @param url Full URL to fetch.
     * @return Response body as a string.
     * @throws java.io.IOException on network or HTTP errors.
     */
    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode != HTTP_OK) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Progress update emission interval during scanning. */
    private const val PROGRESS_UPDATE_INTERVAL_MS = 250L

    /** HTTP 200 status code. */
    private const val HTTP_OK = 200

    /** Number of octets in an IPv4 address. */
    private const val OCTET_COUNT = 4

    /** Default Ollama port. */
    private const val PORT_OLLAMA = 11434

    /** Default LM Studio port. */
    private const val PORT_LM_STUDIO = 1234

    /** Default vLLM port. */
    private const val PORT_VLLM = 8000

    /** Default LocalAI port. */
    private const val PORT_LOCALAI = 8080
}
