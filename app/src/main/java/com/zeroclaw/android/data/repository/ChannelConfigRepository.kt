/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for connected channel configuration.
 *
 * Non-secret configuration values are persisted in Room while secret
 * values (bot tokens, passwords) are stored in EncryptedSharedPreferences.
 */
interface ChannelConfigRepository {
    /** Observable list of all connected channels. */
    val channels: Flow<List<ConnectedChannel>>

    /**
     * Returns the channel with the given [id], or null if not found.
     *
     * @param id Unique channel identifier.
     * @return The matching [ConnectedChannel] or null.
     */
    suspend fun getById(id: String): ConnectedChannel?

    /**
     * Checks whether a channel of the given [type] already exists.
     *
     * @param type Channel type to check.
     * @return True if a channel of this type is configured.
     */
    suspend fun existsForType(type: ChannelType): Boolean

    /**
     * Saves a channel, splitting secret values into encrypted storage.
     *
     * @param channel The channel to persist (non-secret config in [ConnectedChannel.configValues]).
     * @param secrets Map of secret field keys to their plaintext values.
     */
    suspend fun save(channel: ConnectedChannel, secrets: Map<String, String>)

    /**
     * Deletes the channel with the given [id] and all associated secrets.
     *
     * @param id Unique channel identifier.
     */
    suspend fun delete(id: String)

    /**
     * Toggles the enabled state of the channel with the given [id].
     *
     * @param id Unique channel identifier.
     */
    suspend fun toggleEnabled(id: String)

    /**
     * Retrieves the secret field values for a channel.
     *
     * @param channelId Unique channel identifier.
     * @return Map of secret field keys to their plaintext values.
     */
    fun getSecrets(channelId: String): Map<String, String>

    /**
     * Returns all enabled channels with their secret values merged in.
     *
     * Used by [com.zeroclaw.android.service.ConfigTomlBuilder] to generate
     * the TOML configuration for the daemon.
     *
     * @return List of pairs: (channel, all config values including secrets).
     */
    suspend fun getEnabledWithSecrets(): List<Pair<ConnectedChannel, Map<String, String>>>
}
