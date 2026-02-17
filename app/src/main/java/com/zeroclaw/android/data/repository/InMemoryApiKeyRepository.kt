/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Volatile in-memory [ApiKeyRepository] for use as a last-resort fallback
 * when the Android Keystore is completely unusable.
 *
 * Data stored here does not survive process restarts. The [storageHealth]
 * is always [StorageHealth.Degraded].
 */
class InMemoryApiKeyRepository : ApiKeyRepository {
    private val store = mutableMapOf<String, ApiKey>()
    private val _keys = MutableStateFlow<List<ApiKey>>(emptyList())

    override val keys: Flow<List<ApiKey>> = _keys.asStateFlow()

    override val storageHealth: StorageHealth = StorageHealth.Degraded

    override val corruptKeyCount: StateFlow<Int> = MutableStateFlow(0)

    override suspend fun getById(id: String): ApiKey? = store[id]

    override suspend fun save(apiKey: ApiKey) {
        store[apiKey.id] = apiKey
        _keys.value = store.values.sortedByDescending { it.createdAt }
    }

    override suspend fun delete(id: String) {
        store.remove(id)
        _keys.value = store.values.sortedByDescending { it.createdAt }
    }

    override suspend fun exportAll(passphrase: String): String {
        val array = JSONArray()
        store.values.forEach { key ->
            array.put(
                JSONObject().apply {
                    put(JSON_KEY_ID, key.id)
                    put(JSON_KEY_PROVIDER, key.provider)
                    put(JSON_KEY_KEY, key.key)
                    put(JSON_KEY_CREATED_AT, key.createdAt)
                    put(JSON_KEY_STATUS, key.status.name)
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
            save(
                ApiKey(
                    id = java.util.UUID.randomUUID().toString(),
                    provider = provider,
                    key = key,
                    createdAt = obj.optLong(JSON_KEY_CREATED_AT, System.currentTimeMillis()),
                    status = parseKeyStatus(obj.optString(JSON_KEY_STATUS, KeyStatus.ACTIVE.name)),
                ),
            )
            count++
        }
        return count
    }

    override suspend fun getByProvider(provider: String): ApiKey? =
        store.values.find { it.provider.equals(provider, ignoreCase = true) }

    /** Constants for [InMemoryApiKeyRepository]. */
    companion object {
        private const val MAX_IMPORT_KEYS = 100
        private const val MAX_PROVIDER_LENGTH = 100
        private const val MAX_KEY_LENGTH = 1000
        private const val JSON_KEY_ID = "id"
        private const val JSON_KEY_PROVIDER = "provider"
        private const val JSON_KEY_KEY = "key"
        private const val JSON_KEY_CREATED_AT = "created_at"
        private const val JSON_KEY_STATUS = "status"

        private fun parseKeyStatus(value: String): KeyStatus =
            try {
                KeyStatus.valueOf(value)
            } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
                KeyStatus.UNKNOWN
            }
    }
}
