/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.CheckStatus
import com.zeroclaw.android.model.DiagnosticCategory
import com.zeroclaw.android.model.DiagnosticCheck
import com.zeroclaw.android.model.KeyStatus
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.isExpired
import com.zeroclaw.android.model.isOAuthToken
import com.zeroclaw.android.util.BatteryOptimization
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.getStatus
import com.zeroclaw.ffi.validateConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Runs diagnostic checks against the ZeroClaw configuration, API keys,
 * connectivity, daemon health, and system prerequisites.
 *
 * Each check category produces a list of [DiagnosticCheck] results that
 * can be displayed in the Doctor screen. All FFI calls and I/O are
 * dispatched to [ioDispatcher].
 *
 * @param context Application context for system service access.
 * @param agentRepository Repository for reading agent configurations.
 * @param apiKeyRepository Repository for reading API key status.
 * @param ioDispatcher Dispatcher for blocking FFI calls and I/O.
 */
class DoctorValidator(
    private val context: Context,
    private val agentRepository: AgentRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Validates each agent's TOML config individually and the combined config.
     *
     * Uses the FFI [validateConfig] function which parses TOML without
     * starting the daemon.
     *
     * @param preloadedAgents Optional pre-loaded agent list to avoid a
     *   redundant database query. When null, agents are fetched from
     *   [agentRepository].
     * @return List of diagnostic checks for the config category.
     */
    suspend fun runConfigChecks(
        preloadedAgents: List<Agent>? = null,
    ): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        val agents = preloadedAgents ?: agentRepository.agents.first()

        checks.add(checkNameUniqueness(agents))

        val enabledAgents = agents.filter { it.isEnabled }
        for (agent in enabledAgents) {
            checks.add(checkAgentConfig(agent))
        }

        return checks
    }

    /**
     * Checks that API keys exist for each enabled agent's provider.
     *
     * Self-hosted providers (those with [ProviderAuthType.URL_ONLY] or
     * [ProviderAuthType.NONE]) are exempt from the key requirement.
     *
     * @param preloadedAgents Optional pre-loaded agent list to avoid a
     *   redundant database query. When null, agents are fetched from
     *   [agentRepository].
     * @return List of diagnostic checks for the API keys category.
     */
    suspend fun runApiKeyChecks(
        preloadedAgents: List<Agent>? = null,
    ): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        val agents = preloadedAgents ?: agentRepository.agents.first()
        val keys = apiKeyRepository.keys.first()
        val enabledAgents = agents.filter { it.isEnabled }

        for (agent in enabledAgents) {
            checks.add(checkAgentApiKey(agent, keys))
        }

        if (enabledAgents.isEmpty()) {
            checks.add(
                DiagnosticCheck(
                    id = "apikey-none",
                    category = DiagnosticCategory.API_KEYS,
                    title = "No enabled connections",
                    status = CheckStatus.WARN,
                    detail = "Enable at least one connection to validate API keys",
                    actionLabel = "Connections",
                    actionRoute = "agents",
                ),
            )
        }

        return checks
    }

    /**
     * Checks network connectivity.
     *
     * @return List of diagnostic checks for the connectivity category.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun runConnectivityChecks(): List<DiagnosticCheck> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }
        val hasInternet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val detail =
            when {
                !hasInternet -> "No internet connection available"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                    "Connected via Wi-Fi"
                else -> "Connected via cellular"
            }

        return listOf(
            DiagnosticCheck(
                id = "connectivity-network",
                category = DiagnosticCategory.CONNECTIVITY,
                title = "Network connectivity",
                status = if (hasInternet) CheckStatus.PASS else CheckStatus.FAIL,
                detail = detail,
            ),
        )
    }

    /**
     * Parses [getStatus] JSON and checks component staleness.
     *
     * @return List of diagnostic checks for the daemon health category.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun runDaemonHealthChecks(): List<DiagnosticCheck> =
        try {
            val json = withContext(ioDispatcher) { getStatus() }
            parseDaemonStatus(JSONObject(json))
        } catch (e: FfiException) {
            listOf(daemonErrorCheck("Failed to query status: ${e.message}"))
        } catch (e: Exception) {
            listOf(daemonErrorCheck("Unexpected error: ${e.message}"))
        }

    /**
     * Checks system-level prerequisites: battery optimization, OEM
     * detection, storage space, and notification permission (Android 13+).
     *
     * @return List of diagnostic checks for the system category.
     */
    fun runSystemChecks(): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        checks.add(checkBatteryExemption())
        checks.add(checkOemBattery())
        checks.add(checkStorage())
        checkNotificationPermission()?.let { checks.add(it) }
        return checks
    }

    private fun checkNameUniqueness(agents: List<Agent>): DiagnosticCheck {
        val duplicates =
            agents
                .map { it.name }
                .groupBy { it }
                .filter { it.value.size > 1 }
                .keys
        return if (duplicates.isNotEmpty()) {
            DiagnosticCheck(
                id = "config-duplicate-names",
                category = DiagnosticCategory.CONFIG,
                title = "Connection nickname uniqueness",
                status = CheckStatus.FAIL,
                detail = "Duplicate connection nicknames: ${duplicates.joinToString()}",
                actionLabel = "Edit Connections",
                actionRoute = "agents",
            )
        } else {
            DiagnosticCheck(
                id = "config-duplicate-names",
                category = DiagnosticCategory.CONFIG,
                title = "Connection nickname uniqueness",
                status = CheckStatus.PASS,
                detail = "${agents.size} connections, all nicknames unique",
            )
        }
    }

    private suspend fun checkAgentConfig(agent: Agent): DiagnosticCheck {
        val toml = buildAgentToml(agent)
        val result = withContext(ioDispatcher) { validateConfig(toml) }
        return if (result.isEmpty()) {
            DiagnosticCheck(
                id = "config-agent-${agent.id}",
                category = DiagnosticCategory.CONFIG,
                title = "Connection: ${agent.name}",
                status = CheckStatus.PASS,
                detail = "TOML config parses successfully",
            )
        } else {
            DiagnosticCheck(
                id = "config-agent-${agent.id}",
                category = DiagnosticCategory.CONFIG,
                title = "Connection: ${agent.name}",
                status = CheckStatus.FAIL,
                detail = result,
                actionLabel = "Edit Connection",
                actionRoute = "agent-detail/${agent.id}",
            )
        }
    }

    private fun checkAgentApiKey(
        agent: Agent,
        keys: List<ApiKey>,
    ): DiagnosticCheck {
        val providerInfo = ProviderRegistry.findById(agent.provider)
        val isSelfHosted =
            providerInfo?.authType == ProviderAuthType.URL_ONLY ||
                providerInfo?.authType == ProviderAuthType.NONE
        if (isSelfHosted) {
            return DiagnosticCheck(
                id = "apikey-${agent.id}",
                category = DiagnosticCategory.API_KEYS,
                title = "${agent.name} (${agent.provider})",
                status = CheckStatus.PASS,
                detail = "Self-hosted provider, no API key required",
            )
        }

        val matchingKey = keys.find { it.provider.equals(agent.provider, ignoreCase = true) }
        return classifyApiKeyStatus(agent, matchingKey)
    }

    private fun classifyApiKeyStatus(
        agent: Agent,
        key: ApiKey?,
    ): DiagnosticCheck {
        val title = "${agent.name} (${agent.provider})"
        return when {
            key == null ->
                DiagnosticCheck(
                    id = "apikey-${agent.id}",
                    category = DiagnosticCategory.API_KEYS,
                    title = title,
                    status = CheckStatus.FAIL,
                    detail = "No API key found for provider \"${agent.provider}\"",
                    actionLabel = "Add Key",
                    actionRoute = "api-keys",
                )
            key.status == KeyStatus.INVALID ->
                DiagnosticCheck(
                    id = "apikey-${agent.id}",
                    category = DiagnosticCategory.API_KEYS,
                    title = title,
                    status = CheckStatus.FAIL,
                    detail = "API key marked as invalid by provider",
                    actionLabel = "Edit Key",
                    actionRoute = "api-key-detail/${key.id}",
                )
            key.isOAuthToken && key.isExpired() ->
                DiagnosticCheck(
                    id = "apikey-${agent.id}",
                    category = DiagnosticCategory.API_KEYS,
                    title = title,
                    status = CheckStatus.WARN,
                    detail = "OAuth token expired or expiring soon",
                    actionLabel = "Refresh",
                    actionRoute = "api-key-detail/${key.id}",
                )
            else ->
                DiagnosticCheck(
                    id = "apikey-${agent.id}",
                    category = DiagnosticCategory.API_KEYS,
                    title = title,
                    status = CheckStatus.PASS,
                    detail = "Key present and active",
                )
        }
    }

    private fun parseDaemonStatus(obj: JSONObject): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        val daemonRunning = obj.optBoolean("daemon_running", false)

        checks.add(
            DiagnosticCheck(
                id = "daemon-running",
                category = DiagnosticCategory.DAEMON_HEALTH,
                title = "Daemon process",
                status = if (daemonRunning) CheckStatus.PASS else CheckStatus.WARN,
                detail = if (daemonRunning) "Daemon is running" else "Daemon is not running",
            ),
        )

        if (daemonRunning) {
            checks.add(
                DiagnosticCheck(
                    id = "daemon-uptime",
                    category = DiagnosticCategory.DAEMON_HEALTH,
                    title = "Daemon uptime",
                    status = CheckStatus.PASS,
                    detail = formatUptime(obj.optLong("uptime_seconds", 0)),
                ),
            )
            checks.addAll(parseComponentStatuses(obj))
        }

        return checks
    }

    private fun parseComponentStatuses(obj: JSONObject): List<DiagnosticCheck> {
        val componentsObj = obj.optJSONObject("components") ?: return emptyList()
        return componentsObj
            .keys()
            .asSequence()
            .map { key ->
                val status = componentsObj.optJSONObject(key)?.optString("status", "unknown") ?: "unknown"
                DiagnosticCheck(
                    id = "daemon-component-$key",
                    category = DiagnosticCategory.DAEMON_HEALTH,
                    title = "Component: $key",
                    status = if (status == "ok") CheckStatus.PASS else CheckStatus.FAIL,
                    detail = "Status: $status",
                )
            }.toList()
    }

    private fun daemonErrorCheck(detail: String): DiagnosticCheck =
        DiagnosticCheck(
            id = "daemon-error",
            category = DiagnosticCategory.DAEMON_HEALTH,
            title = "Daemon health check",
            status = CheckStatus.FAIL,
            detail = detail,
        )

    private fun checkBatteryExemption(): DiagnosticCheck {
        val isExempt = BatteryOptimization.isExempt(context)
        return DiagnosticCheck(
            id = "system-battery-exempt",
            category = DiagnosticCategory.SYSTEM,
            title = "Battery optimization",
            status = if (isExempt) CheckStatus.PASS else CheckStatus.WARN,
            detail =
                if (isExempt) {
                    "App is exempt from battery optimization"
                } else {
                    "App is not exempt; service may be killed"
                },
            actionLabel = if (!isExempt) "Fix" else null,
            actionRoute = if (!isExempt) "battery-settings" else null,
        )
    }

    private fun checkOemBattery(): DiagnosticCheck {
        val aggressiveOem = BatteryOptimization.detectAggressiveOem()
        return if (aggressiveOem != null) {
            DiagnosticCheck(
                id = "system-oem",
                category = DiagnosticCategory.SYSTEM,
                title = "OEM battery management",
                status = CheckStatus.WARN,
                detail = "Device uses aggressive battery management (${aggressiveOem.name})",
                actionLabel = "Instructions",
                actionRoute = "battery-settings",
            )
        } else {
            DiagnosticCheck(
                id = "system-oem",
                category = DiagnosticCategory.SYSTEM,
                title = "OEM battery management",
                status = CheckStatus.PASS,
                detail = "No aggressive OEM battery management detected",
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkStorage(): DiagnosticCheck =
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableMb = stat.availableBytes / BYTES_PER_MB
            val storageStatus =
                when {
                    availableMb < LOW_STORAGE_THRESHOLD_MB -> CheckStatus.FAIL
                    availableMb < WARN_STORAGE_THRESHOLD_MB -> CheckStatus.WARN
                    else -> CheckStatus.PASS
                }
            DiagnosticCheck(
                id = "system-storage",
                category = DiagnosticCategory.SYSTEM,
                title = "Available storage",
                status = storageStatus,
                detail = "${availableMb}MB available",
            )
        } catch (_: Exception) {
            DiagnosticCheck(
                id = "system-storage",
                category = DiagnosticCategory.SYSTEM,
                title = "Available storage",
                status = CheckStatus.WARN,
                detail = "Could not determine available storage",
            )
        }

    @Suppress("TooGenericExceptionCaught")
    private fun checkNotificationPermission(): DiagnosticCheck? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                DiagnosticCheck(
                    id = "system-notification",
                    category = DiagnosticCategory.SYSTEM,
                    title = "Notification permission",
                    status = if (granted) CheckStatus.PASS else CheckStatus.WARN,
                    detail =
                        if (granted) {
                            "Notification permission granted"
                        } else {
                            "Notifications disabled; service status won't show"
                        },
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Builds a minimal TOML config string for validating a single agent.
     *
     * @param agent The agent to build TOML for.
     * @return TOML string suitable for [validateConfig].
     */
    private fun buildAgentToml(agent: Agent): String {
        val entry =
            AgentTomlEntry(
                name = agent.name,
                provider = ConfigTomlBuilder.resolveProvider(agent.provider, ""),
                model = agent.modelName,
                systemPrompt = agent.systemPrompt,
                temperature = agent.temperature,
                maxDepth = agent.maxDepth,
            )
        return "default_temperature = 0.7\n" + ConfigTomlBuilder.buildAgentsToml(listOf(entry))
    }

    /** Constants for [DoctorValidator]. */
    companion object {
        private const val BYTES_PER_MB = 1_048_576L
        private const val LOW_STORAGE_THRESHOLD_MB = 50L
        private const val WARN_STORAGE_THRESHOLD_MB = 200L

        /**
         * Formats an uptime duration in seconds to a human-readable string.
         *
         * @param seconds Total uptime seconds.
         * @return Formatted string like "2h 15m 30s".
         */
        fun formatUptime(seconds: Long): String {
            val hours = seconds / SECONDS_PER_HOUR
            val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            val secs = seconds % SECONDS_PER_MINUTE
            return when {
                hours > 0 -> "${hours}h ${minutes}m ${secs}s"
                minutes > 0 -> "${minutes}m ${secs}s"
                else -> "${secs}s"
            }
        }

        private const val SECONDS_PER_HOUR = 3600L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
