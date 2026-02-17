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

/**
 * Repository interface for API key CRUD operations.
 *
 * Implementations must store keys securely (e.g. via
 * [EncryptedSharedPreferences][androidx.security.crypto.EncryptedSharedPreferences]).
 */
interface ApiKeyRepository {
    /** Observable stream of all stored API keys. */
    val keys: Flow<List<ApiKey>>

    /**
     * Health state of the underlying encrypted storage backend.
     *
     * Defaults to [StorageHealth.Healthy] for implementations that do not
     * use the Android Keystore (e.g. in-memory test doubles).
     */
    val storageHealth: StorageHealth
        get() = StorageHealth.Healthy

    /**
     * Number of stored entries that could not be deserialized on the last load.
     *
     * A non-zero value indicates data corruption. Defaults to a constant zero
     * flow for implementations that do not track corruption.
     */
    val corruptKeyCount: StateFlow<Int>
        get() = MutableStateFlow(0)

    /**
     * Retrieves a single key by its identifier.
     *
     * @param id Key identifier.
     * @return The matching [ApiKey] or null if not found.
     */
    suspend fun getById(id: String): ApiKey?

    /**
     * Adds or updates an API key.
     *
     * If a key with the same [ApiKey.id] exists, it is replaced.
     *
     * @param apiKey The key to save.
     */
    suspend fun save(apiKey: ApiKey)

    /**
     * Deletes an API key by its identifier.
     *
     * @param id Key identifier to delete.
     */
    suspend fun delete(id: String)

    /**
     * Exports all stored keys as an encrypted JSON string.
     *
     * The JSON is encrypted with AES-256-GCM using a key derived from
     * [passphrase] via PBKDF2WithHmacSHA256 (600,000 iterations). The
     * output format is Base64-encoded: `salt(16) || iv(12) || ciphertext || tag(16)`.
     *
     * Safe to call from the main thread; performs blocking crypto on the
     * calling dispatcher.
     *
     * @param passphrase User-provided encryption passphrase.
     * @return Base64-encoded encrypted payload.
     * @throws java.security.GeneralSecurityException if encryption fails.
     */
    suspend fun exportAll(passphrase: String): String

    /**
     * Imports keys from an encrypted JSON string.
     *
     * Decrypts the [encryptedPayload] using [passphrase], then upserts
     * each key into the store. Generates fresh UUIDs for imported keys
     * to prevent ID collision attacks.
     *
     * @param encryptedPayload Base64-encoded encrypted data from [exportAll].
     * @param passphrase The passphrase used during export.
     * @return Number of keys successfully imported.
     * @throws java.security.GeneralSecurityException if decryption or MAC
     *   verification fails.
     * @throws IllegalArgumentException if the payload format is invalid.
     */
    suspend fun importFrom(encryptedPayload: String, passphrase: String): Int

    /**
     * Retrieves the first key matching the given provider.
     *
     * Resolves aliases via [ProviderRegistry][com.zeroclaw.android.data.ProviderRegistry]
     * so that e.g. "grok" finds a key stored under "xai".
     *
     * @param provider Provider ID or alias to search for.
     * @return The matching [ApiKey] or null if none exists.
     */
    suspend fun getByProvider(provider: String): ApiKey?

    /**
     * Updates the [KeyStatus] of a stored key.
     *
     * Default implementation loads the key, copies with the new status, and
     * saves. Implementations may override for more efficient updates.
     *
     * @param id Key identifier.
     * @param status New status to set.
     */
    suspend fun markKeyStatus(id: String, status: KeyStatus) {
        getById(id)?.copy(status = status)?.let { save(it) }
    }
}
