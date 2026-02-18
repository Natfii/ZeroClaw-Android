// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ToolSpec
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiToolSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust tools browsing FFI.
 *
 * Wraps the tools-related UniFFI-generated function in a coroutine-safe
 * suspend function dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class ToolsBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Lists all available tools based on daemon config and installed skills.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return List of all [ToolSpec] instances.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun listTools(): List<ToolSpec> =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .listTools()
                .map { it.toModel() }
        }
}

/**
 * Converts an FFI tool spec record to the domain model.
 *
 * @receiver FFI-generated [FfiToolSpec] record from the native layer.
 * @return Domain [ToolSpec] model with identical field values.
 */
private fun FfiToolSpec.toModel(): ToolSpec =
    ToolSpec(
        name = name,
        description = description,
        source = source,
        parametersJson = parametersJson,
    )
