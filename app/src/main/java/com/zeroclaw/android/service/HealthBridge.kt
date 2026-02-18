/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ComponentHealth
import com.zeroclaw.android.model.HealthDetail
import com.zeroclaw.ffi.FfiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust structured health FFI.
 *
 * Wraps [com.zeroclaw.ffi.getHealthDetail] and [com.zeroclaw.ffi.getComponentHealth]
 * in coroutine-safe suspend functions dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class HealthBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Fetches structured health detail for all daemon components.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return Parsed [HealthDetail] snapshot.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getHealthDetail(): HealthDetail =
        withContext(ioDispatcher) {
            val ffi = com.zeroclaw.ffi.getHealthDetail()
            HealthDetail(
                daemonRunning = ffi.daemonRunning,
                pid = ffi.pid.toLong(),
                uptimeSeconds = ffi.uptimeSeconds.toLong(),
                components =
                    ffi.components.map { c ->
                        ComponentHealth(
                            name = c.name,
                            status = c.status,
                            lastError = c.lastError,
                            restartCount = c.restartCount.toLong(),
                        )
                    },
            )
        }

    /**
     * Fetches health for a single named component.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param name Component name (e.g. "gateway").
     * @return [ComponentHealth] or null if the component is not registered.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun getComponentHealth(name: String): ComponentHealth? =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.getComponentHealth(name)?.let { c ->
                ComponentHealth(
                    name = c.name,
                    status = c.status,
                    lastError = c.lastError,
                    restartCount = c.restartCount.toLong(),
                )
            }
        }
}
