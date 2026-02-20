/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extension property providing the singleton [DataStore] for app settings. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

/**
 * [SettingsRepository] implementation backed by Jetpack DataStore Preferences.
 *
 * Each setting is stored as a separate preference key. The [settings] flow
 * emits a new [AppSettings] whenever any key changes.
 *
 * @param context Application context for DataStore initialization.
 */
@Suppress("TooManyFunctions")
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            mapPrefsToSettings(prefs)
        }

    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod", "LongMethod")
    private fun mapPrefsToSettings(prefs: Preferences): AppSettings =
        AppSettings(
            host = prefs[KEY_HOST] ?: AppSettings.DEFAULT_HOST,
            port = prefs[KEY_PORT] ?: AppSettings.DEFAULT_PORT,
            autoStartOnBoot = prefs[KEY_AUTO_START] ?: false,
            defaultProvider = prefs[KEY_DEFAULT_PROVIDER] ?: "",
            defaultModel = prefs[KEY_DEFAULT_MODEL] ?: "",
            defaultTemperature =
                prefs[KEY_DEFAULT_TEMPERATURE]
                    ?: AppSettings.DEFAULT_TEMPERATURE,
            compactContext = prefs[KEY_COMPACT_CONTEXT] ?: false,
            costEnabled = prefs[KEY_COST_ENABLED] ?: false,
            dailyLimitUsd =
                prefs[KEY_DAILY_LIMIT_USD]
                    ?: AppSettings.DEFAULT_DAILY_LIMIT_USD,
            monthlyLimitUsd =
                prefs[KEY_MONTHLY_LIMIT_USD]
                    ?: AppSettings.DEFAULT_MONTHLY_LIMIT_USD,
            costWarnAtPercent =
                prefs[KEY_COST_WARN_PERCENT]
                    ?: AppSettings.DEFAULT_COST_WARN_PERCENT,
            providerRetries =
                prefs[KEY_PROVIDER_RETRIES]
                    ?: AppSettings.DEFAULT_PROVIDER_RETRIES,
            fallbackProviders = prefs[KEY_FALLBACK_PROVIDERS] ?: "",
            memoryBackend =
                prefs[KEY_MEMORY_BACKEND]
                    ?: AppSettings.DEFAULT_MEMORY_BACKEND,
            memoryAutoSave = prefs[KEY_MEMORY_AUTO_SAVE] ?: true,
            identityJson = prefs[KEY_IDENTITY_JSON] ?: "",
            autonomyLevel =
                prefs[KEY_AUTONOMY_LEVEL]
                    ?: AppSettings.DEFAULT_AUTONOMY_LEVEL,
            workspaceOnly = prefs[KEY_WORKSPACE_ONLY] ?: true,
            allowedCommands =
                prefs[KEY_ALLOWED_COMMANDS]
                    ?: AppSettings.DEFAULT_ALLOWED_COMMANDS,
            forbiddenPaths =
                prefs[KEY_FORBIDDEN_PATHS]
                    ?: AppSettings.DEFAULT_FORBIDDEN_PATHS,
            maxActionsPerHour =
                prefs[KEY_MAX_ACTIONS_PER_HOUR]
                    ?: AppSettings.DEFAULT_MAX_ACTIONS_PER_HOUR,
            maxCostPerDayCents =
                prefs[KEY_MAX_COST_PER_DAY_CENTS]
                    ?: AppSettings.DEFAULT_MAX_COST_PER_DAY_CENTS,
            requireApprovalMediumRisk = prefs[KEY_REQUIRE_APPROVAL_MEDIUM_RISK] ?: true,
            blockHighRiskCommands = prefs[KEY_BLOCK_HIGH_RISK] ?: true,
            tunnelProvider = prefs[KEY_TUNNEL_PROVIDER] ?: "none",
            tunnelCloudflareToken = prefs[KEY_TUNNEL_CF_TOKEN] ?: "",
            tunnelTailscaleFunnel = prefs[KEY_TUNNEL_TS_FUNNEL] ?: false,
            tunnelTailscaleHostname = prefs[KEY_TUNNEL_TS_HOSTNAME] ?: "",
            tunnelNgrokAuthToken = prefs[KEY_TUNNEL_NGROK_TOKEN] ?: "",
            tunnelNgrokDomain = prefs[KEY_TUNNEL_NGROK_DOMAIN] ?: "",
            tunnelCustomCommand = prefs[KEY_TUNNEL_CUSTOM_CMD] ?: "",
            tunnelCustomHealthUrl = prefs[KEY_TUNNEL_CUSTOM_HEALTH] ?: "",
            tunnelCustomUrlPattern = prefs[KEY_TUNNEL_CUSTOM_PATTERN] ?: "",
            gatewayRequirePairing = prefs[KEY_GW_REQUIRE_PAIRING] ?: false,
            gatewayAllowPublicBind = prefs[KEY_GW_ALLOW_PUBLIC] ?: false,
            gatewayPairedTokens = prefs[KEY_GW_PAIRED_TOKENS] ?: "",
            gatewayPairRateLimit =
                prefs[KEY_GW_PAIR_RATE]
                    ?: AppSettings.DEFAULT_PAIR_RATE_LIMIT,
            gatewayWebhookRateLimit =
                prefs[KEY_GW_WEBHOOK_RATE]
                    ?: AppSettings.DEFAULT_WEBHOOK_RATE_LIMIT,
            gatewayIdempotencyTtl =
                prefs[KEY_GW_IDEMPOTENCY_TTL]
                    ?: AppSettings.DEFAULT_IDEMPOTENCY_TTL,
            schedulerEnabled = prefs[KEY_SCHEDULER_ENABLED] ?: true,
            schedulerMaxTasks =
                prefs[KEY_SCHEDULER_MAX_TASKS]
                    ?: AppSettings.DEFAULT_SCHEDULER_MAX_TASKS,
            schedulerMaxConcurrent =
                prefs[KEY_SCHEDULER_MAX_CONCURRENT]
                    ?: AppSettings.DEFAULT_SCHEDULER_MAX_CONCURRENT,
            heartbeatEnabled = prefs[KEY_HEARTBEAT_ENABLED] ?: false,
            heartbeatIntervalMinutes =
                prefs[KEY_HEARTBEAT_INTERVAL]
                    ?: AppSettings.DEFAULT_HEARTBEAT_INTERVAL,
            observabilityBackend = prefs[KEY_OBS_BACKEND] ?: "none",
            observabilityOtelEndpoint =
                prefs[KEY_OBS_OTEL_ENDPOINT]
                    ?: AppSettings.DEFAULT_OTEL_ENDPOINT,
            observabilityOtelServiceName =
                prefs[KEY_OBS_OTEL_SERVICE]
                    ?: AppSettings.DEFAULT_OTEL_SERVICE_NAME,
            modelRoutesJson = prefs[KEY_MODEL_ROUTES_JSON] ?: "[]",
            memoryHygieneEnabled = prefs[KEY_MEMORY_HYGIENE] ?: true,
            memoryArchiveAfterDays =
                prefs[KEY_MEMORY_ARCHIVE_DAYS]
                    ?: AppSettings.DEFAULT_ARCHIVE_DAYS,
            memoryPurgeAfterDays =
                prefs[KEY_MEMORY_PURGE_DAYS]
                    ?: AppSettings.DEFAULT_PURGE_DAYS,
            memoryEmbeddingProvider = prefs[KEY_MEMORY_EMBED_PROVIDER] ?: "none",
            memoryEmbeddingModel =
                prefs[KEY_MEMORY_EMBED_MODEL]
                    ?: AppSettings.DEFAULT_EMBEDDING_MODEL,
            memoryVectorWeight =
                prefs[KEY_MEMORY_VECTOR_WEIGHT]
                    ?: AppSettings.DEFAULT_VECTOR_WEIGHT,
            memoryKeywordWeight =
                prefs[KEY_MEMORY_KEYWORD_WEIGHT]
                    ?: AppSettings.DEFAULT_KEYWORD_WEIGHT,
            composioEnabled = prefs[KEY_COMPOSIO_ENABLED] ?: false,
            composioApiKey = prefs[KEY_COMPOSIO_API_KEY] ?: "",
            composioEntityId = prefs[KEY_COMPOSIO_ENTITY_ID] ?: "default",
            browserEnabled = prefs[KEY_BROWSER_ENABLED] ?: false,
            browserAllowedDomains = prefs[KEY_BROWSER_DOMAINS] ?: "",
            httpRequestEnabled = prefs[KEY_HTTP_REQ_ENABLED] ?: false,
            httpRequestAllowedDomains = prefs[KEY_HTTP_REQ_DOMAINS] ?: "",
            biometricForService = prefs[KEY_BIOMETRIC_SERVICE] ?: false,
            biometricForSettings = prefs[KEY_BIOMETRIC_SETTINGS] ?: false,
            lockEnabled = prefs[KEY_LOCK_ENABLED] ?: false,
            lockTimeoutMinutes =
                prefs[KEY_LOCK_TIMEOUT]
                    ?: AppSettings.DEFAULT_LOCK_TIMEOUT,
            pinHash = prefs[KEY_PIN_HASH] ?: "",
            biometricUnlockEnabled = prefs[KEY_BIOMETRIC_UNLOCK] ?: false,
            pluginRegistryUrl =
                prefs[KEY_PLUGIN_REGISTRY_URL]
                    ?: AppSettings.DEFAULT_PLUGIN_REGISTRY_URL,
            pluginSyncEnabled = prefs[KEY_PLUGIN_SYNC_ENABLED] ?: false,
            pluginSyncIntervalHours =
                prefs[KEY_PLUGIN_SYNC_INTERVAL]
                    ?: AppSettings.DEFAULT_PLUGIN_SYNC_INTERVAL,
            lastPluginSyncTimestamp = prefs[KEY_LAST_PLUGIN_SYNC] ?: 0L,
            stripThinkingTags = prefs[KEY_STRIP_THINKING_TAGS] ?: false,
            theme =
                prefs[KEY_THEME]?.let { name ->
                    runCatching { ThemeMode.valueOf(name) }.getOrNull()
                } ?: ThemeMode.SYSTEM,
        )

    override suspend fun setHost(host: String) = edit { it[KEY_HOST] = host }

    override suspend fun setPort(port: Int) = edit { it[KEY_PORT] = port }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) = edit { it[KEY_AUTO_START] = enabled }

    override suspend fun setDefaultProvider(provider: String) = edit { it[KEY_DEFAULT_PROVIDER] = provider }

    override suspend fun setDefaultModel(model: String) = edit { it[KEY_DEFAULT_MODEL] = model }

    override suspend fun setDefaultTemperature(temperature: Float) = edit { it[KEY_DEFAULT_TEMPERATURE] = temperature }

    override suspend fun setCompactContext(enabled: Boolean) = edit { it[KEY_COMPACT_CONTEXT] = enabled }

    override suspend fun setCostEnabled(enabled: Boolean) = edit { it[KEY_COST_ENABLED] = enabled }

    override suspend fun setDailyLimitUsd(limit: Float) = edit { it[KEY_DAILY_LIMIT_USD] = limit }

    override suspend fun setMonthlyLimitUsd(limit: Float) = edit { it[KEY_MONTHLY_LIMIT_USD] = limit }

    override suspend fun setCostWarnAtPercent(percent: Int) = edit { it[KEY_COST_WARN_PERCENT] = percent }

    override suspend fun setProviderRetries(retries: Int) = edit { it[KEY_PROVIDER_RETRIES] = retries }

    override suspend fun setFallbackProviders(providers: String) = edit { it[KEY_FALLBACK_PROVIDERS] = providers }

    override suspend fun setMemoryBackend(backend: String) = edit { it[KEY_MEMORY_BACKEND] = backend }

    override suspend fun setMemoryAutoSave(enabled: Boolean) = edit { it[KEY_MEMORY_AUTO_SAVE] = enabled }

    override suspend fun setIdentityJson(json: String) = edit { it[KEY_IDENTITY_JSON] = json }

    override suspend fun setAutonomyLevel(level: String) = edit { it[KEY_AUTONOMY_LEVEL] = level }

    override suspend fun setWorkspaceOnly(enabled: Boolean) = edit { it[KEY_WORKSPACE_ONLY] = enabled }

    override suspend fun setAllowedCommands(commands: String) = edit { it[KEY_ALLOWED_COMMANDS] = commands }

    override suspend fun setForbiddenPaths(paths: String) = edit { it[KEY_FORBIDDEN_PATHS] = paths }

    override suspend fun setMaxActionsPerHour(max: Int) = edit { it[KEY_MAX_ACTIONS_PER_HOUR] = max }

    override suspend fun setMaxCostPerDayCents(cents: Int) = edit { it[KEY_MAX_COST_PER_DAY_CENTS] = cents }

    override suspend fun setRequireApprovalMediumRisk(required: Boolean) = edit { it[KEY_REQUIRE_APPROVAL_MEDIUM_RISK] = required }

    override suspend fun setBlockHighRiskCommands(blocked: Boolean) = edit { it[KEY_BLOCK_HIGH_RISK] = blocked }

    override suspend fun setTunnelProvider(provider: String) = edit { it[KEY_TUNNEL_PROVIDER] = provider }

    override suspend fun setTunnelCloudflareToken(token: String) = edit { it[KEY_TUNNEL_CF_TOKEN] = token }

    override suspend fun setTunnelTailscaleFunnel(enabled: Boolean) = edit { it[KEY_TUNNEL_TS_FUNNEL] = enabled }

    override suspend fun setTunnelTailscaleHostname(hostname: String) = edit { it[KEY_TUNNEL_TS_HOSTNAME] = hostname }

    override suspend fun setTunnelNgrokAuthToken(token: String) = edit { it[KEY_TUNNEL_NGROK_TOKEN] = token }

    override suspend fun setTunnelNgrokDomain(domain: String) = edit { it[KEY_TUNNEL_NGROK_DOMAIN] = domain }

    override suspend fun setTunnelCustomCommand(command: String) = edit { it[KEY_TUNNEL_CUSTOM_CMD] = command }

    override suspend fun setTunnelCustomHealthUrl(url: String) = edit { it[KEY_TUNNEL_CUSTOM_HEALTH] = url }

    override suspend fun setTunnelCustomUrlPattern(pattern: String) = edit { it[KEY_TUNNEL_CUSTOM_PATTERN] = pattern }

    override suspend fun setGatewayRequirePairing(required: Boolean) = edit { it[KEY_GW_REQUIRE_PAIRING] = required }

    override suspend fun setGatewayAllowPublicBind(allowed: Boolean) = edit { it[KEY_GW_ALLOW_PUBLIC] = allowed }

    override suspend fun setGatewayPairedTokens(tokens: String) = edit { it[KEY_GW_PAIRED_TOKENS] = tokens }

    override suspend fun setGatewayPairRateLimit(limit: Int) = edit { it[KEY_GW_PAIR_RATE] = limit }

    override suspend fun setGatewayWebhookRateLimit(limit: Int) = edit { it[KEY_GW_WEBHOOK_RATE] = limit }

    override suspend fun setGatewayIdempotencyTtl(seconds: Int) = edit { it[KEY_GW_IDEMPOTENCY_TTL] = seconds }

    override suspend fun setSchedulerEnabled(enabled: Boolean) = edit { it[KEY_SCHEDULER_ENABLED] = enabled }

    override suspend fun setSchedulerMaxTasks(max: Int) = edit { it[KEY_SCHEDULER_MAX_TASKS] = max }

    override suspend fun setSchedulerMaxConcurrent(max: Int) = edit { it[KEY_SCHEDULER_MAX_CONCURRENT] = max }

    override suspend fun setHeartbeatEnabled(enabled: Boolean) = edit { it[KEY_HEARTBEAT_ENABLED] = enabled }

    override suspend fun setHeartbeatIntervalMinutes(minutes: Int) = edit { it[KEY_HEARTBEAT_INTERVAL] = minutes }

    override suspend fun setObservabilityBackend(backend: String) = edit { it[KEY_OBS_BACKEND] = backend }

    override suspend fun setObservabilityOtelEndpoint(endpoint: String) = edit { it[KEY_OBS_OTEL_ENDPOINT] = endpoint }

    override suspend fun setObservabilityOtelServiceName(name: String) = edit { it[KEY_OBS_OTEL_SERVICE] = name }

    override suspend fun setModelRoutesJson(json: String) = edit { it[KEY_MODEL_ROUTES_JSON] = json }

    override suspend fun setMemoryHygieneEnabled(enabled: Boolean) = edit { it[KEY_MEMORY_HYGIENE] = enabled }

    override suspend fun setMemoryArchiveAfterDays(days: Int) = edit { it[KEY_MEMORY_ARCHIVE_DAYS] = days }

    override suspend fun setMemoryPurgeAfterDays(days: Int) = edit { it[KEY_MEMORY_PURGE_DAYS] = days }

    override suspend fun setMemoryEmbeddingProvider(provider: String) = edit { it[KEY_MEMORY_EMBED_PROVIDER] = provider }

    override suspend fun setMemoryEmbeddingModel(model: String) = edit { it[KEY_MEMORY_EMBED_MODEL] = model }

    override suspend fun setMemoryVectorWeight(weight: Float) = edit { it[KEY_MEMORY_VECTOR_WEIGHT] = weight }

    override suspend fun setMemoryKeywordWeight(weight: Float) = edit { it[KEY_MEMORY_KEYWORD_WEIGHT] = weight }

    override suspend fun setComposioEnabled(enabled: Boolean) = edit { it[KEY_COMPOSIO_ENABLED] = enabled }

    override suspend fun setComposioApiKey(key: String) = edit { it[KEY_COMPOSIO_API_KEY] = key }

    override suspend fun setComposioEntityId(entityId: String) = edit { it[KEY_COMPOSIO_ENTITY_ID] = entityId }

    override suspend fun setBrowserEnabled(enabled: Boolean) = edit { it[KEY_BROWSER_ENABLED] = enabled }

    override suspend fun setBrowserAllowedDomains(domains: String) = edit { it[KEY_BROWSER_DOMAINS] = domains }

    override suspend fun setHttpRequestEnabled(enabled: Boolean) = edit { it[KEY_HTTP_REQ_ENABLED] = enabled }

    override suspend fun setHttpRequestAllowedDomains(domains: String) = edit { it[KEY_HTTP_REQ_DOMAINS] = domains }

    override suspend fun setLockEnabled(enabled: Boolean) = edit { it[KEY_LOCK_ENABLED] = enabled }

    override suspend fun setLockTimeoutMinutes(minutes: Int) = edit { it[KEY_LOCK_TIMEOUT] = minutes }

    override suspend fun setPinHash(hash: String) = edit { it[KEY_PIN_HASH] = hash }

    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = edit { it[KEY_BIOMETRIC_UNLOCK] = enabled }

    override suspend fun setPluginRegistryUrl(url: String) = edit { it[KEY_PLUGIN_REGISTRY_URL] = url }

    override suspend fun setPluginSyncEnabled(enabled: Boolean) = edit { it[KEY_PLUGIN_SYNC_ENABLED] = enabled }

    override suspend fun setPluginSyncIntervalHours(hours: Int) = edit { it[KEY_PLUGIN_SYNC_INTERVAL] = hours }

    override suspend fun setLastPluginSyncTimestamp(timestamp: Long) = edit { it[KEY_LAST_PLUGIN_SYNC] = timestamp }

    override suspend fun setStripThinkingTags(enabled: Boolean) = edit { it[KEY_STRIP_THINKING_TAGS] = enabled }

    override suspend fun setTheme(theme: ThemeMode) = edit { it[KEY_THEME] = theme.name }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { prefs -> transform(prefs) }
    }

    /** DataStore preference keys for [DataStoreSettingsRepository]. */
    @Suppress("MemberNameEqualsClassName")
    private companion object {
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_PORT = intPreferencesKey("port")
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val KEY_DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        val KEY_DEFAULT_TEMPERATURE = floatPreferencesKey("default_temperature")
        val KEY_COMPACT_CONTEXT = booleanPreferencesKey("compact_context")
        val KEY_COST_ENABLED = booleanPreferencesKey("cost_enabled")
        val KEY_DAILY_LIMIT_USD = floatPreferencesKey("daily_limit_usd")
        val KEY_MONTHLY_LIMIT_USD = floatPreferencesKey("monthly_limit_usd")
        val KEY_COST_WARN_PERCENT = intPreferencesKey("cost_warn_at_percent")
        val KEY_PROVIDER_RETRIES = intPreferencesKey("provider_retries")
        val KEY_FALLBACK_PROVIDERS = stringPreferencesKey("fallback_providers")
        val KEY_MEMORY_BACKEND = stringPreferencesKey("memory_backend")
        val KEY_MEMORY_AUTO_SAVE = booleanPreferencesKey("memory_auto_save")
        val KEY_IDENTITY_JSON = stringPreferencesKey("identity_json")
        val KEY_AUTONOMY_LEVEL = stringPreferencesKey("autonomy_level")
        val KEY_WORKSPACE_ONLY = booleanPreferencesKey("workspace_only")
        val KEY_ALLOWED_COMMANDS = stringPreferencesKey("allowed_commands")
        val KEY_FORBIDDEN_PATHS = stringPreferencesKey("forbidden_paths")
        val KEY_MAX_ACTIONS_PER_HOUR = intPreferencesKey("max_actions_per_hour")
        val KEY_MAX_COST_PER_DAY_CENTS = intPreferencesKey("max_cost_per_day_cents")
        val KEY_REQUIRE_APPROVAL_MEDIUM_RISK =
            booleanPreferencesKey("require_approval_medium_risk")
        val KEY_BLOCK_HIGH_RISK = booleanPreferencesKey("block_high_risk_commands")
        val KEY_TUNNEL_PROVIDER = stringPreferencesKey("tunnel_provider")
        val KEY_TUNNEL_CF_TOKEN = stringPreferencesKey("tunnel_cf_token")
        val KEY_TUNNEL_TS_FUNNEL = booleanPreferencesKey("tunnel_ts_funnel")
        val KEY_TUNNEL_TS_HOSTNAME = stringPreferencesKey("tunnel_ts_hostname")
        val KEY_TUNNEL_NGROK_TOKEN = stringPreferencesKey("tunnel_ngrok_token")
        val KEY_TUNNEL_NGROK_DOMAIN = stringPreferencesKey("tunnel_ngrok_domain")
        val KEY_TUNNEL_CUSTOM_CMD = stringPreferencesKey("tunnel_custom_cmd")
        val KEY_TUNNEL_CUSTOM_HEALTH = stringPreferencesKey("tunnel_custom_health")
        val KEY_TUNNEL_CUSTOM_PATTERN = stringPreferencesKey("tunnel_custom_pattern")
        val KEY_GW_REQUIRE_PAIRING = booleanPreferencesKey("gw_require_pairing")
        val KEY_GW_ALLOW_PUBLIC = booleanPreferencesKey("gw_allow_public_bind")
        val KEY_GW_PAIRED_TOKENS = stringPreferencesKey("gw_paired_tokens")
        val KEY_GW_PAIR_RATE = intPreferencesKey("gw_pair_rate_limit")
        val KEY_GW_WEBHOOK_RATE = intPreferencesKey("gw_webhook_rate_limit")
        val KEY_GW_IDEMPOTENCY_TTL = intPreferencesKey("gw_idempotency_ttl")
        val KEY_SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val KEY_SCHEDULER_MAX_TASKS = intPreferencesKey("scheduler_max_tasks")
        val KEY_SCHEDULER_MAX_CONCURRENT = intPreferencesKey("scheduler_max_concurrent")
        val KEY_HEARTBEAT_ENABLED = booleanPreferencesKey("heartbeat_enabled")
        val KEY_HEARTBEAT_INTERVAL = intPreferencesKey("heartbeat_interval")
        val KEY_OBS_BACKEND = stringPreferencesKey("observability_backend")
        val KEY_OBS_OTEL_ENDPOINT = stringPreferencesKey("obs_otel_endpoint")
        val KEY_OBS_OTEL_SERVICE = stringPreferencesKey("obs_otel_service")
        val KEY_MODEL_ROUTES_JSON = stringPreferencesKey("model_routes_json")
        val KEY_MEMORY_HYGIENE = booleanPreferencesKey("memory_hygiene_enabled")
        val KEY_MEMORY_ARCHIVE_DAYS = intPreferencesKey("memory_archive_days")
        val KEY_MEMORY_PURGE_DAYS = intPreferencesKey("memory_purge_days")
        val KEY_MEMORY_EMBED_PROVIDER = stringPreferencesKey("memory_embed_provider")
        val KEY_MEMORY_EMBED_MODEL = stringPreferencesKey("memory_embed_model")
        val KEY_MEMORY_VECTOR_WEIGHT = floatPreferencesKey("memory_vector_weight")
        val KEY_MEMORY_KEYWORD_WEIGHT = floatPreferencesKey("memory_keyword_weight")
        val KEY_COMPOSIO_ENABLED = booleanPreferencesKey("composio_enabled")
        val KEY_COMPOSIO_API_KEY = stringPreferencesKey("composio_api_key")
        val KEY_COMPOSIO_ENTITY_ID = stringPreferencesKey("composio_entity_id")
        val KEY_BROWSER_ENABLED = booleanPreferencesKey("browser_enabled")
        val KEY_BROWSER_DOMAINS = stringPreferencesKey("browser_domains")
        val KEY_HTTP_REQ_ENABLED = booleanPreferencesKey("http_req_enabled")
        val KEY_HTTP_REQ_DOMAINS = stringPreferencesKey("http_req_domains")
        val KEY_BIOMETRIC_SERVICE = booleanPreferencesKey("biometric_for_service")
        val KEY_BIOMETRIC_SETTINGS = booleanPreferencesKey("biometric_for_settings")
        val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_LOCK_TIMEOUT = intPreferencesKey("lock_timeout_minutes")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock_enabled")
        val KEY_PLUGIN_REGISTRY_URL = stringPreferencesKey("plugin_registry_url")
        val KEY_PLUGIN_SYNC_ENABLED = booleanPreferencesKey("plugin_sync_enabled")
        val KEY_PLUGIN_SYNC_INTERVAL = intPreferencesKey("plugin_sync_interval_hours")
        val KEY_LAST_PLUGIN_SYNC = longPreferencesKey("last_plugin_sync_timestamp")
        val KEY_STRIP_THINKING_TAGS = booleanPreferencesKey("strip_thinking_tags")
        val KEY_THEME = stringPreferencesKey("theme")
    }
}
