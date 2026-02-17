/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.data.SecurePrefsProvider
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * [ApiKeyRepository] implementation backed by encrypted [SharedPreferences].
 *
 * Keys are stored as JSON objects in encrypted preferences, indexed by
 * their unique identifier. The [keys] flow is updated after every
 * mutation to keep observers in sync.
 *
 * Uses [SecurePrefsProvider] for resilient keystore access with automatic
 * recovery from corruption. The [storageHealth] property indicates whether
 * the underlying storage is fully functional, was recovered, or fell back
 * to volatile in-memory storage.
 *
 * @param context Application context for EncryptedSharedPreferences initialization.
 * @param ioScope Background [CoroutineScope] for deferring the initial key load off
 *   the main thread. When null (e.g. in tests), keys are loaded synchronously
 *   in the constructor.
 * @param prefsOverride Optional pre-built [SharedPreferences] for testing.
 *   When provided, [storageHealth] defaults to [StorageHealth.Healthy].
 */
class EncryptedApiKeyRepository(
    context: Context? = null,
    private val ioScope: CoroutineScope? = null,
    prefsOverride: SharedPreferences? = null,
) : ApiKeyRepository {
    override val storageHealth: StorageHealth

    private val prefs: SharedPreferences

    init {
        if (prefsOverride != null) {
            prefs = prefsOverride
            storageHealth = StorageHealth.Healthy
        } else {
            requireNotNull(context) { "context required when prefsOverride is null" }
            val (created, health) = SecurePrefsProvider.create(context, PREFS_NAME)
            prefs = created
            storageHealth = health
        }
    }

    private val _corruptKeyCount = MutableStateFlow(0)
    override val corruptKeyCount: StateFlow<Int> = _corruptKeyCount.asStateFlow()

    private val _keys = MutableStateFlow<List<ApiKey>>(emptyList())

    init {
        if (ioScope != null) {
            ioScope.launch { _keys.value = loadAll() }
        } else {
            _keys.value = loadAll()
        }
    }

    override val keys: Flow<List<ApiKey>> = _keys.asStateFlow()

    override suspend fun getById(id: String): ApiKey? =
        _keys.value.find { it.id == id }

    override suspend fun save(apiKey: ApiKey) {
        val json =
            JSONObject().apply {
                put(JSON_KEY_ID, apiKey.id)
                put(JSON_KEY_PROVIDER, apiKey.provider)
                put(JSON_KEY_KEY, apiKey.key)
                put(JSON_KEY_BASE_URL, apiKey.baseUrl)
                put(JSON_KEY_CREATED_AT, apiKey.createdAt)
                put(JSON_KEY_STATUS, apiKey.status.name)
                put(JSON_KEY_REFRESH_TOKEN, apiKey.refreshToken)
                put(JSON_KEY_EXPIRES_AT, apiKey.expiresAt)
            }
        prefs.edit().putString(apiKey.id, json.toString()).apply()
        _keys.value = loadAll()
    }

    override suspend fun delete(id: String) {
        prefs.edit().remove(id).apply()
        _keys.value = loadAll()
    }

    override suspend fun exportAll(passphrase: String): String {
        val array = JSONArray()
        _keys.value.forEach { key ->
            array.put(
                JSONObject().apply {
                    put(JSON_KEY_ID, key.id)
                    put(JSON_KEY_PROVIDER, key.provider)
                    put(JSON_KEY_KEY, key.key)
                    put(JSON_KEY_BASE_URL, key.baseUrl)
                    put(JSON_KEY_CREATED_AT, key.createdAt)
                    put(JSON_KEY_STATUS, key.status.name)
                    put(JSON_KEY_REFRESH_TOKEN, key.refreshToken)
                    put(JSON_KEY_EXPIRES_AT, key.expiresAt)
                },
            )
        }
        return KeyExportCrypto.encrypt(array.toString(), passphrase)
    }

    override suspend fun importFrom(
        encryptedPayload: String,
        passphrase: String,
    ): Int {
        val json = KeyExportCrypto.decrypt(encryptedPayload, passphrase)
        val array = JSONArray(json)
        require(array.length() <= MAX_IMPORT_KEYS) {
            "Import contains ${array.length()} keys, maximum is $MAX_IMPORT_KEYS"
        }
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val provider = obj.getString(JSON_KEY_PROVIDER)
            val key = obj.getString(JSON_KEY_KEY)
            if (provider.length > MAX_PROVIDER_LENGTH || key.length > MAX_KEY_LENGTH) {
                continue
            }
            val baseUrl = obj.optString(JSON_KEY_BASE_URL, "")
            if (baseUrl.length > MAX_BASE_URL_LENGTH) {
                continue
            }
            val apiKey =
                ApiKey(
                    id = java.util.UUID.randomUUID().toString(),
                    provider = provider,
                    key = key,
                    baseUrl = baseUrl,
                    createdAt = obj.optLong(JSON_KEY_CREATED_AT, System.currentTimeMillis()),
                    status = parseKeyStatus(obj.optString(JSON_KEY_STATUS, KeyStatus.ACTIVE.name)),
                    refreshToken = obj.optString(JSON_KEY_REFRESH_TOKEN, ""),
                    expiresAt = obj.optLong(JSON_KEY_EXPIRES_AT, 0L),
                )
            save(apiKey)
            count++
        }
        return count
    }

    override suspend fun getByProvider(provider: String): ApiKey? {
        val resolved = ProviderRegistry.findById(provider)
        val targetId = resolved?.id ?: provider.lowercase()
        val targetAliases = resolved?.aliases.orEmpty().map { it.lowercase() }.toSet() + targetId
        return _keys.value.firstOrNull { key ->
            val keyResolved = ProviderRegistry.findById(key.provider)
            val keyId = keyResolved?.id ?: key.provider.lowercase()
            keyId in targetAliases || targetAliases.contains(keyId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadAll(): List<ApiKey> {
        var corrupt = 0
        val loaded =
            prefs.all.mapNotNull { (_, value) ->
                try {
                    parseApiKey(value as String)
                } catch (e: Exception) {
                    corrupt++
                    Log.w(TAG, "Skipping corrupt key entry: ${e.message}")
                    null
                }
            }.sortedByDescending { it.createdAt }
        _corruptKeyCount.value = corrupt
        return loaded
    }

    /** Constants for [EncryptedApiKeyRepository]. */
    companion object {
        private const val TAG = "EncryptedApiKeyRepo"
        private const val PREFS_NAME = "zeroclaw_api_keys"
        private const val MAX_IMPORT_KEYS = 100
        private const val MAX_PROVIDER_LENGTH = 100
        private const val MAX_KEY_LENGTH = 1000
        private const val MAX_BASE_URL_LENGTH = 2000
        private const val JSON_KEY_ID = "id"
        private const val JSON_KEY_PROVIDER = "provider"
        private const val JSON_KEY_KEY = "key"
        private const val JSON_KEY_BASE_URL = "base_url"
        private const val JSON_KEY_CREATED_AT = "created_at"
        private const val JSON_KEY_STATUS = "status"
        private const val JSON_KEY_REFRESH_TOKEN = "refresh_token"
        private const val JSON_KEY_EXPIRES_AT = "expires_at"

        private fun parseApiKey(json: String): ApiKey {
            val obj = JSONObject(json)
            return ApiKey(
                id = obj.getString(JSON_KEY_ID),
                provider = obj.getString(JSON_KEY_PROVIDER),
                key = obj.getString(JSON_KEY_KEY),
                baseUrl = obj.optString(JSON_KEY_BASE_URL, ""),
                createdAt = obj.optLong(JSON_KEY_CREATED_AT, 0L),
                status = parseKeyStatus(obj.optString(JSON_KEY_STATUS, KeyStatus.ACTIVE.name)),
                refreshToken = obj.optString(JSON_KEY_REFRESH_TOKEN, ""),
                expiresAt = obj.optLong(JSON_KEY_EXPIRES_AT, 0L),
            )
        }

        private fun parseKeyStatus(value: String): KeyStatus =
            try {
                KeyStatus.valueOf(value)
            } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
                KeyStatus.UNKNOWN
            }
    }
}
