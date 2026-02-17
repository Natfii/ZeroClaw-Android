/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

import com.zeroclaw.android.service.KeyErrorType

/**
 * Represents a provider rejecting an API key during an FFI operation.
 *
 * Emitted by [DaemonServiceBridge][com.zeroclaw.android.service.DaemonServiceBridge]
 * when the daemon's [send][com.zeroclaw.android.service.DaemonServiceBridge.send]
 * method encounters an authentication or rate-limit error.
 *
 * @property detail The original error message from the FFI layer.
 * @property errorType Classification of the rejection.
 * @property timestamp Epoch milliseconds when the rejection was detected.
 */
data class KeyRejectionEvent(
    val detail: String,
    val errorType: KeyErrorType,
    val timestamp: Long = System.currentTimeMillis(),
)
