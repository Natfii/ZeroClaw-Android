/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import android.content.SharedPreferences
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
import com.zeroclaw.android.data.SecurePrefsProvider
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Extension property providing the singleton [DataStore] for app settings. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

/** Name for the EncryptedSharedPreferences file storing secret settings. */
private const val SECURE_SETTINGS_PREFS = "secure_settings"

/**
 * [SettingsRepository] implementation backed by Jetpack DataStore Preferences.
 *
 * Non-secret settings are stored in plaintext DataStore. Secret values
 * (tunnel tokens, third-party API keys, paired gateway tokens, the PIN
 * hash, and reliability API keys) are stored in [EncryptedSharedPreferences]
 * via [SecurePrefsProvider].
 *
 * @param context Application context for DataStore initialization.
 */
@Suppress("TooManyFunctions")
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    private val securePrefs: SharedPreferences =
        SecurePrefsProvider.create(context, SECURE_SETTINGS_PREFS).first

    private val secureRevision = MutableStateFlow(0)

    override val settings: Flow<AppSettings> =
        combine(
            context.settingsDataStore.data,
            secureRevision,
        ) { prefs, _ ->
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
            tunnelCloudflareToken = securePrefs.getString(SEC_TUNNEL_CF_TOKEN, "") ?: "",
            tunnelTailscaleFunnel = prefs[KEY_TUNNEL_TS_FUNNEL] ?: false,
            tunnelTailscaleHostname = prefs[KEY_TUNNEL_TS_HOSTNAME] ?: "",
            tunnelNgrokAuthToken = securePrefs.getString(SEC_TUNNEL_NGROK_TOKEN, "") ?: "",
            tunnelNgrokDomain = prefs[KEY_TUNNEL_NGROK_DOMAIN] ?: "",
            tunnelCustomCommand = prefs[KEY_TUNNEL_CUSTOM_CMD] ?: "",
            tunnelCustomHealthUrl = prefs[KEY_TUNNEL_CUSTOM_HEALTH] ?: "",
            tunnelCustomUrlPattern = prefs[KEY_TUNNEL_CUSTOM_PATTERN] ?: "",
            gatewayRequirePairing = prefs[KEY_GW_REQUIRE_PAIRING] ?: false,
            gatewayAllowPublicBind = prefs[KEY_GW_ALLOW_PUBLIC] ?: false,
            gatewayPairedTokens = securePrefs.getString(SEC_GW_PAIRED_TOKENS, "") ?: "",
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
            composioApiKey = securePrefs.getString(SEC_COMPOSIO_API_KEY, "") ?: "",
            composioEntityId = prefs[KEY_COMPOSIO_ENTITY_ID] ?: "default",
            browserEnabled = prefs[KEY_BROWSER_ENABLED] ?: false,
            browserAllowedDomains = prefs[KEY_BROWSER_DOMAINS] ?: "",
            httpRequestEnabled = prefs[KEY_HTTP_REQ_ENABLED] ?: false,
            httpRequestAllowedDomains = prefs[KEY_HTTP_REQ_DOMAINS] ?: "",
            lockEnabled = prefs[KEY_LOCK_ENABLED] ?: false,
            lockTimeoutMinutes =
                prefs[KEY_LOCK_TIMEOUT]
                    ?: AppSettings.DEFAULT_LOCK_TIMEOUT,
            pinHash = securePrefs.getString(SEC_PIN_HASH, "") ?: "",
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
            webFetchEnabled = prefs[KEY_WEB_FETCH_ENABLED] ?: false,
            webFetchAllowedDomains = prefs[KEY_WEB_FETCH_ALLOWED_DOMAINS] ?: "",
            webFetchBlockedDomains = prefs[KEY_WEB_FETCH_BLOCKED_DOMAINS] ?: "",
            webFetchMaxResponseSize =
                prefs[KEY_WEB_FETCH_MAX_RESPONSE_SIZE]
                    ?: AppSettings.DEFAULT_WEB_FETCH_MAX_RESPONSE_SIZE,
            webFetchTimeoutSecs =
                prefs[KEY_WEB_FETCH_TIMEOUT_SECS]
                    ?: AppSettings.DEFAULT_WEB_FETCH_TIMEOUT_SECS,
            webSearchEnabled = prefs[KEY_WEB_SEARCH_ENABLED] ?: false,
            webSearchProvider =
                prefs[KEY_WEB_SEARCH_PROVIDER]
                    ?: AppSettings.DEFAULT_WEB_SEARCH_PROVIDER,
            webSearchBraveApiKey = securePrefs.getString(SEC_WEB_SEARCH_BRAVE_API_KEY, "") ?: "",
            webSearchMaxResults =
                prefs[KEY_WEB_SEARCH_MAX_RESULTS]
                    ?: AppSettings.DEFAULT_WEB_SEARCH_MAX_RESULTS,
            webSearchTimeoutSecs =
                prefs[KEY_WEB_SEARCH_TIMEOUT_SECS]
                    ?: AppSettings.DEFAULT_WEB_SEARCH_TIMEOUT_SECS,
            securitySandboxEnabled = prefs[KEY_SECURITY_SANDBOX_ENABLED] ?: "",
            securitySandboxBackend =
                prefs[KEY_SECURITY_SANDBOX_BACKEND]
                    ?: AppSettings.DEFAULT_SANDBOX_BACKEND,
            securitySandboxFirejailArgs = prefs[KEY_SECURITY_SANDBOX_FIREJAIL_ARGS] ?: "",
            securityResourcesMaxMemoryMb =
                prefs[KEY_SECURITY_RESOURCES_MAX_MEMORY]
                    ?: AppSettings.DEFAULT_RESOURCES_MAX_MEMORY_MB,
            securityResourcesMaxCpuTimeSecs =
                prefs[KEY_SECURITY_RESOURCES_MAX_CPU]
                    ?: AppSettings.DEFAULT_RESOURCES_MAX_CPU_TIME_SECS,
            securityResourcesMaxSubprocesses =
                prefs[KEY_SECURITY_RESOURCES_MAX_SUBPROCS]
                    ?: AppSettings.DEFAULT_RESOURCES_MAX_SUBPROCESSES,
            securityResourcesMemoryMonitoring = prefs[KEY_SECURITY_RESOURCES_MONITORING] ?: true,
            securityAuditEnabled = prefs[KEY_SECURITY_AUDIT_ENABLED] ?: false,
            securityOtpEnabled = prefs[KEY_SECURITY_OTP_ENABLED] ?: false,
            securityOtpMethod =
                prefs[KEY_SECURITY_OTP_METHOD]
                    ?: AppSettings.DEFAULT_OTP_METHOD,
            securityOtpTokenTtlSecs =
                prefs[KEY_SECURITY_OTP_TOKEN_TTL]
                    ?: AppSettings.DEFAULT_OTP_TOKEN_TTL_SECS,
            securityOtpCacheValidSecs =
                prefs[KEY_SECURITY_OTP_CACHE_VALID]
                    ?: AppSettings.DEFAULT_OTP_CACHE_VALID_SECS,
            securityOtpGatedActions =
                prefs[KEY_SECURITY_OTP_GATED_ACTIONS]
                    ?: AppSettings.DEFAULT_OTP_GATED_ACTIONS,
            securityOtpGatedDomains = prefs[KEY_SECURITY_OTP_GATED_DOMAINS] ?: "",
            securityOtpGatedDomainCategories = prefs[KEY_SECURITY_OTP_GATED_DOMAIN_CATS] ?: "",
            securityEstopEnabled = prefs[KEY_SECURITY_ESTOP_ENABLED] ?: false,
            securityEstopRequireOtpToResume = prefs[KEY_SECURITY_ESTOP_REQUIRE_OTP] ?: true,
            memoryQdrantUrl = prefs[KEY_MEMORY_QDRANT_URL] ?: "",
            memoryQdrantCollection =
                prefs[KEY_MEMORY_QDRANT_COLLECTION]
                    ?: AppSettings.DEFAULT_QDRANT_COLLECTION,
            memoryQdrantApiKey = securePrefs.getString(SEC_MEMORY_QDRANT_API_KEY, "") ?: "",
            embeddingRoutesJson = prefs[KEY_EMBEDDING_ROUTES_JSON] ?: "[]",
            queryClassificationEnabled = prefs[KEY_QUERY_CLASSIFICATION_ENABLED] ?: false,
            proxyEnabled = prefs[KEY_PROXY_ENABLED] ?: false,
            proxyHttpProxy = prefs[KEY_PROXY_HTTP_PROXY] ?: "",
            proxyHttpsProxy = prefs[KEY_PROXY_HTTPS_PROXY] ?: "",
            proxyAllProxy = prefs[KEY_PROXY_ALL_PROXY] ?: "",
            proxyNoProxy = prefs[KEY_PROXY_NO_PROXY] ?: "",
            proxyScope =
                prefs[KEY_PROXY_SCOPE]
                    ?: AppSettings.DEFAULT_PROXY_SCOPE,
            proxyServiceSelectors = prefs[KEY_PROXY_SERVICE_SELECTORS] ?: "",
            reliabilityBackoffMs =
                prefs[KEY_RELIABILITY_BACKOFF_MS]
                    ?: AppSettings.DEFAULT_RELIABILITY_BACKOFF_MS,
            reliabilityApiKeysJson =
                securePrefs.getString(SEC_RELIABILITY_API_KEYS_JSON, "{}") ?: "{}",
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

    override suspend fun setTunnelCloudflareToken(token: String) = editSecure(SEC_TUNNEL_CF_TOKEN, token)

    override suspend fun setTunnelTailscaleFunnel(enabled: Boolean) = edit { it[KEY_TUNNEL_TS_FUNNEL] = enabled }

    override suspend fun setTunnelTailscaleHostname(hostname: String) = edit { it[KEY_TUNNEL_TS_HOSTNAME] = hostname }

    override suspend fun setTunnelNgrokAuthToken(token: String) = editSecure(SEC_TUNNEL_NGROK_TOKEN, token)

    override suspend fun setTunnelNgrokDomain(domain: String) = edit { it[KEY_TUNNEL_NGROK_DOMAIN] = domain }

    override suspend fun setTunnelCustomCommand(command: String) = edit { it[KEY_TUNNEL_CUSTOM_CMD] = command }

    override suspend fun setTunnelCustomHealthUrl(url: String) = edit { it[KEY_TUNNEL_CUSTOM_HEALTH] = url }

    override suspend fun setTunnelCustomUrlPattern(pattern: String) = edit { it[KEY_TUNNEL_CUSTOM_PATTERN] = pattern }

    override suspend fun setGatewayRequirePairing(required: Boolean) = edit { it[KEY_GW_REQUIRE_PAIRING] = required }

    override suspend fun setGatewayAllowPublicBind(allowed: Boolean) = edit { it[KEY_GW_ALLOW_PUBLIC] = allowed }

    override suspend fun setGatewayPairedTokens(tokens: String) = editSecure(SEC_GW_PAIRED_TOKENS, tokens)

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

    override suspend fun setComposioApiKey(key: String) = editSecure(SEC_COMPOSIO_API_KEY, key)

    override suspend fun setComposioEntityId(entityId: String) = edit { it[KEY_COMPOSIO_ENTITY_ID] = entityId }

    override suspend fun setBrowserEnabled(enabled: Boolean) = edit { it[KEY_BROWSER_ENABLED] = enabled }

    override suspend fun setBrowserAllowedDomains(domains: String) = edit { it[KEY_BROWSER_DOMAINS] = domains }

    override suspend fun setHttpRequestEnabled(enabled: Boolean) = edit { it[KEY_HTTP_REQ_ENABLED] = enabled }

    override suspend fun setHttpRequestAllowedDomains(domains: String) = edit { it[KEY_HTTP_REQ_DOMAINS] = domains }

    override suspend fun setWebFetchEnabled(enabled: Boolean) = edit { it[KEY_WEB_FETCH_ENABLED] = enabled }

    override suspend fun setWebFetchAllowedDomains(domains: String) = edit { it[KEY_WEB_FETCH_ALLOWED_DOMAINS] = domains }

    override suspend fun setWebFetchBlockedDomains(domains: String) = edit { it[KEY_WEB_FETCH_BLOCKED_DOMAINS] = domains }

    override suspend fun setWebFetchMaxResponseSize(size: Int) = edit { it[KEY_WEB_FETCH_MAX_RESPONSE_SIZE] = size }

    override suspend fun setWebFetchTimeoutSecs(secs: Int) = edit { it[KEY_WEB_FETCH_TIMEOUT_SECS] = secs }

    override suspend fun setWebSearchEnabled(enabled: Boolean) = edit { it[KEY_WEB_SEARCH_ENABLED] = enabled }

    override suspend fun setWebSearchProvider(provider: String) = edit { it[KEY_WEB_SEARCH_PROVIDER] = provider }

    override suspend fun setWebSearchBraveApiKey(key: String) = editSecure(SEC_WEB_SEARCH_BRAVE_API_KEY, key)

    override suspend fun setWebSearchMaxResults(max: Int) = edit { it[KEY_WEB_SEARCH_MAX_RESULTS] = max }

    override suspend fun setWebSearchTimeoutSecs(secs: Int) = edit { it[KEY_WEB_SEARCH_TIMEOUT_SECS] = secs }

    override suspend fun setSecuritySandboxEnabled(enabled: String) = edit { it[KEY_SECURITY_SANDBOX_ENABLED] = enabled }

    override suspend fun setSecuritySandboxBackend(backend: String) = edit { it[KEY_SECURITY_SANDBOX_BACKEND] = backend }

    override suspend fun setSecuritySandboxFirejailArgs(args: String) = edit { it[KEY_SECURITY_SANDBOX_FIREJAIL_ARGS] = args }

    override suspend fun setSecurityResourcesMaxMemoryMb(mb: Int) = edit { it[KEY_SECURITY_RESOURCES_MAX_MEMORY] = mb }

    override suspend fun setSecurityResourcesMaxCpuTimeSecs(secs: Int) = edit { it[KEY_SECURITY_RESOURCES_MAX_CPU] = secs }

    override suspend fun setSecurityResourcesMaxSubprocesses(max: Int) = edit { it[KEY_SECURITY_RESOURCES_MAX_SUBPROCS] = max }

    override suspend fun setSecurityResourcesMemoryMonitoring(enabled: Boolean) = edit { it[KEY_SECURITY_RESOURCES_MONITORING] = enabled }

    override suspend fun setSecurityAuditEnabled(enabled: Boolean) = edit { it[KEY_SECURITY_AUDIT_ENABLED] = enabled }

    override suspend fun setSecurityOtpEnabled(enabled: Boolean) = edit { it[KEY_SECURITY_OTP_ENABLED] = enabled }

    override suspend fun setSecurityOtpMethod(method: String) = edit { it[KEY_SECURITY_OTP_METHOD] = method }

    override suspend fun setSecurityOtpTokenTtlSecs(secs: Int) = edit { it[KEY_SECURITY_OTP_TOKEN_TTL] = secs }

    override suspend fun setSecurityOtpCacheValidSecs(secs: Int) = edit { it[KEY_SECURITY_OTP_CACHE_VALID] = secs }

    override suspend fun setSecurityOtpGatedActions(actions: String) = edit { it[KEY_SECURITY_OTP_GATED_ACTIONS] = actions }

    override suspend fun setSecurityOtpGatedDomains(domains: String) = edit { it[KEY_SECURITY_OTP_GATED_DOMAINS] = domains }

    override suspend fun setSecurityOtpGatedDomainCategories(categories: String) = edit { it[KEY_SECURITY_OTP_GATED_DOMAIN_CATS] = categories }

    override suspend fun setSecurityEstopEnabled(enabled: Boolean) = edit { it[KEY_SECURITY_ESTOP_ENABLED] = enabled }

    override suspend fun setSecurityEstopRequireOtpToResume(required: Boolean) = edit { it[KEY_SECURITY_ESTOP_REQUIRE_OTP] = required }

    override suspend fun setMemoryQdrantUrl(url: String) = edit { it[KEY_MEMORY_QDRANT_URL] = url }

    override suspend fun setMemoryQdrantCollection(collection: String) = edit { it[KEY_MEMORY_QDRANT_COLLECTION] = collection }

    override suspend fun setMemoryQdrantApiKey(key: String) = editSecure(SEC_MEMORY_QDRANT_API_KEY, key)

    override suspend fun setEmbeddingRoutesJson(json: String) = edit { it[KEY_EMBEDDING_ROUTES_JSON] = json }

    override suspend fun setQueryClassificationEnabled(enabled: Boolean) = edit { it[KEY_QUERY_CLASSIFICATION_ENABLED] = enabled }

    override suspend fun setProxyEnabled(enabled: Boolean) = edit { it[KEY_PROXY_ENABLED] = enabled }

    override suspend fun setProxyHttpProxy(proxy: String) = edit { it[KEY_PROXY_HTTP_PROXY] = proxy }

    override suspend fun setProxyHttpsProxy(proxy: String) = edit { it[KEY_PROXY_HTTPS_PROXY] = proxy }

    override suspend fun setProxyAllProxy(proxy: String) = edit { it[KEY_PROXY_ALL_PROXY] = proxy }

    override suspend fun setProxyNoProxy(noProxy: String) = edit { it[KEY_PROXY_NO_PROXY] = noProxy }

    override suspend fun setProxyScope(scope: String) = edit { it[KEY_PROXY_SCOPE] = scope }

    override suspend fun setProxyServiceSelectors(selectors: String) = edit { it[KEY_PROXY_SERVICE_SELECTORS] = selectors }

    override suspend fun setReliabilityBackoffMs(ms: Int) = edit { it[KEY_RELIABILITY_BACKOFF_MS] = ms }

    override suspend fun setReliabilityApiKeysJson(json: String) = editSecure(SEC_RELIABILITY_API_KEYS_JSON, json)

    override suspend fun setLockEnabled(enabled: Boolean) = edit { it[KEY_LOCK_ENABLED] = enabled }

    override suspend fun setLockTimeoutMinutes(minutes: Int) = edit { it[KEY_LOCK_TIMEOUT] = minutes }

    override suspend fun setPinHash(hash: String) = editSecure(SEC_PIN_HASH, hash)

    override suspend fun setPluginRegistryUrl(url: String) = edit { it[KEY_PLUGIN_REGISTRY_URL] = url }

    override suspend fun setPluginSyncEnabled(enabled: Boolean) = edit { it[KEY_PLUGIN_SYNC_ENABLED] = enabled }

    override suspend fun setPluginSyncIntervalHours(hours: Int) = edit { it[KEY_PLUGIN_SYNC_INTERVAL] = hours }

    override suspend fun setLastPluginSyncTimestamp(timestamp: Long) = edit { it[KEY_LAST_PLUGIN_SYNC] = timestamp }

    override suspend fun setStripThinkingTags(enabled: Boolean) = edit { it[KEY_STRIP_THINKING_TAGS] = enabled }

    override suspend fun setTheme(theme: ThemeMode) = edit { it[KEY_THEME] = theme.name }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { prefs -> transform(prefs) }
    }

    private fun editSecure(
        key: String,
        value: String,
    ) {
        securePrefs.edit().putString(key, value).apply()
        secureRevision.value++
    }

    /**
     * DataStore preference keys for non-secret settings and
     * EncryptedSharedPreferences keys (SEC_ prefix) for secrets.
     */
    @Suppress("MemberNameEqualsClassName")
    private companion object {
        const val SEC_TUNNEL_CF_TOKEN = "sec_tunnel_cf_token"
        const val SEC_TUNNEL_NGROK_TOKEN = "sec_tunnel_ngrok_token"
        const val SEC_GW_PAIRED_TOKENS = "sec_gw_paired_tokens"
        const val SEC_COMPOSIO_API_KEY = "sec_composio_api_key"
        const val SEC_WEB_SEARCH_BRAVE_API_KEY = "sec_web_search_brave_api_key"
        const val SEC_MEMORY_QDRANT_API_KEY = "sec_memory_qdrant_api_key"
        const val SEC_PIN_HASH = "sec_pin_hash"
        const val SEC_RELIABILITY_API_KEYS_JSON = "sec_reliability_api_keys_json"

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
        val KEY_WEB_FETCH_ENABLED = booleanPreferencesKey("web_fetch_enabled")
        val KEY_WEB_FETCH_ALLOWED_DOMAINS = stringPreferencesKey("web_fetch_allowed_domains")
        val KEY_WEB_FETCH_BLOCKED_DOMAINS = stringPreferencesKey("web_fetch_blocked_domains")
        val KEY_WEB_FETCH_MAX_RESPONSE_SIZE = intPreferencesKey("web_fetch_max_response_size")
        val KEY_WEB_FETCH_TIMEOUT_SECS = intPreferencesKey("web_fetch_timeout_secs")
        val KEY_WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val KEY_WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val KEY_WEB_SEARCH_BRAVE_API_KEY = stringPreferencesKey("web_search_brave_api_key")
        val KEY_WEB_SEARCH_MAX_RESULTS = intPreferencesKey("web_search_max_results")
        val KEY_WEB_SEARCH_TIMEOUT_SECS = intPreferencesKey("web_search_timeout_secs")
        val KEY_SECURITY_SANDBOX_ENABLED = stringPreferencesKey("security_sandbox_enabled")
        val KEY_SECURITY_SANDBOX_BACKEND = stringPreferencesKey("security_sandbox_backend")
        val KEY_SECURITY_SANDBOX_FIREJAIL_ARGS = stringPreferencesKey("security_sandbox_firejail_args")
        val KEY_SECURITY_RESOURCES_MAX_MEMORY = intPreferencesKey("security_resources_max_memory_mb")
        val KEY_SECURITY_RESOURCES_MAX_CPU = intPreferencesKey("security_resources_max_cpu_time_secs")
        val KEY_SECURITY_RESOURCES_MAX_SUBPROCS = intPreferencesKey("security_resources_max_subprocesses")
        val KEY_SECURITY_RESOURCES_MONITORING = booleanPreferencesKey("security_resources_memory_monitoring")
        val KEY_SECURITY_AUDIT_ENABLED = booleanPreferencesKey("security_audit_enabled")
        val KEY_SECURITY_OTP_ENABLED = booleanPreferencesKey("security_otp_enabled")
        val KEY_SECURITY_OTP_METHOD = stringPreferencesKey("security_otp_method")
        val KEY_SECURITY_OTP_TOKEN_TTL = intPreferencesKey("security_otp_token_ttl_secs")
        val KEY_SECURITY_OTP_CACHE_VALID = intPreferencesKey("security_otp_cache_valid_secs")
        val KEY_SECURITY_OTP_GATED_ACTIONS = stringPreferencesKey("security_otp_gated_actions")
        val KEY_SECURITY_OTP_GATED_DOMAINS = stringPreferencesKey("security_otp_gated_domains")
        val KEY_SECURITY_OTP_GATED_DOMAIN_CATS = stringPreferencesKey("security_otp_gated_domain_categories")
        val KEY_SECURITY_ESTOP_ENABLED = booleanPreferencesKey("security_estop_enabled")
        val KEY_SECURITY_ESTOP_REQUIRE_OTP = booleanPreferencesKey("security_estop_require_otp_to_resume")
        val KEY_MEMORY_QDRANT_URL = stringPreferencesKey("memory_qdrant_url")
        val KEY_MEMORY_QDRANT_COLLECTION = stringPreferencesKey("memory_qdrant_collection")
        val KEY_MEMORY_QDRANT_API_KEY = stringPreferencesKey("memory_qdrant_api_key")
        val KEY_EMBEDDING_ROUTES_JSON = stringPreferencesKey("embedding_routes_json")
        val KEY_QUERY_CLASSIFICATION_ENABLED = booleanPreferencesKey("query_classification_enabled")
        val KEY_PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val KEY_PROXY_HTTP_PROXY = stringPreferencesKey("proxy_http_proxy")
        val KEY_PROXY_HTTPS_PROXY = stringPreferencesKey("proxy_https_proxy")
        val KEY_PROXY_ALL_PROXY = stringPreferencesKey("proxy_all_proxy")
        val KEY_PROXY_NO_PROXY = stringPreferencesKey("proxy_no_proxy")
        val KEY_PROXY_SCOPE = stringPreferencesKey("proxy_scope")
        val KEY_PROXY_SERVICE_SELECTORS = stringPreferencesKey("proxy_service_selectors")
        val KEY_RELIABILITY_BACKOFF_MS = intPreferencesKey("reliability_backoff_ms")
        val KEY_RELIABILITY_API_KEYS_JSON = stringPreferencesKey("reliability_api_keys_json")
    }
}
