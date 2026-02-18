/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

import kotlinx.serialization.Serializable

/**
 * Plugin metadata received from the remote plugin registry.
 *
 * @property id Unique identifier matching the local plugin ID.
 * @property name Human-readable display name.
 * @property description Short description of the plugin's purpose.
 * @property version Latest available version string.
 * @property author Plugin author or organization.
 * @property category Functional category name.
 */
@Serializable
data class RemotePlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String,
)
