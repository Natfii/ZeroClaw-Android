/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

/// Errors that can occur across the FFI boundary.
///
/// Each variant uses a `detail` field (not `message`) to avoid
/// conflicting with Kotlin's `Throwable.message` property.
///
/// # UniFFI naming convention
///
/// UniFFI generates Kotlin exception subclasses under `FfiException` with
/// names matching the Rust variant (e.g. `FfiError::ConfigError` becomes
/// `FfiException.ConfigException`). The `Exception` suffix is added
/// automatically by UniFFI's Kotlin code generator. Kotlin code should
/// pattern-match on these subclasses when distinguishing error categories.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiError {
    /// TOML parse failures, missing fields, or invalid paths.
    #[error("config error: {detail}")]
    ConfigError {
        /// Description of the configuration problem.
        detail: String,
    },

    /// Daemon already running or not running when expected.
    #[error("state error: {detail}")]
    StateError {
        /// Description of the state mismatch.
        detail: String,
    },

    /// Tokio runtime creation failure or component spawn errors.
    #[error("spawn error: {detail}")]
    SpawnError {
        /// Description of the runtime or spawn failure.
        detail: String,
    },

    /// The internal process state is irrecoverably corrupt.
    ///
    /// The only recovery path is restarting the application process.
    #[error("internal state corrupted: {detail}")]
    StateCorrupted {
        /// Description of the corruption.
        detail: String,
    },

    /// Graceful shutdown failures.
    #[error("shutdown error: {detail}")]
    ShutdownError {
        /// Description of the shutdown failure.
        detail: String,
    },

    /// `catch_unwind` caught a panic at the FFI boundary.
    #[error("internal panic: {detail}")]
    InternalPanic {
        /// Description of the panic.
        detail: String,
    },
}
