/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.model.ComponentStatus
import com.zeroclaw.android.model.DaemonStatus
import com.zeroclaw.android.model.KeyRejectionEvent
import com.zeroclaw.android.model.MemoryConflict
import com.zeroclaw.android.model.MemoryHealthResult
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.getStatus
import com.zeroclaw.ffi.scaffoldWorkspace
import com.zeroclaw.ffi.sendMessage
import com.zeroclaw.ffi.startDaemon
import com.zeroclaw.ffi.stopDaemon
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Bridge between the Android service layer and the Rust FFI.
 *
 * Wraps all FFI calls in coroutine-safe suspend functions that dispatch
 * to [Dispatchers.IO] and exposes observable [StateFlow] properties for
 * daemon lifecycle and health. This class is the sole point of contact
 * between Kotlin service/UI code and native code.
 *
 * A single instance is created in
 * [ZeroClawApplication][com.zeroclaw.android.ZeroClawApplication] and
 * shared across the foreground service and ViewModel.
 *
 * Thread-safe: all mutable state is managed through [StateFlow].
 *
 * @param dataDir Absolute path to the app's internal files directory,
 *   typically [android.content.Context.getFilesDir].
 * @param ioDispatcher [CoroutineDispatcher] used for blocking FFI calls.
 *   Defaults to [Dispatchers.IO]; inject a test dispatcher for unit tests.
 */
class DaemonServiceBridge(
    private val dataDir: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Optional [EventBridge] for daemon event callbacks.
     *
     * Set after construction from [ZeroClawApplication.onCreate] because the
     * [EventBridge] is created after this bridge. When non-null, [register] is
     * called after a successful [start] and [unregister] before [stop].
     */
    var eventBridge: EventBridge? = null

    init {
        require(dataDir.isNotEmpty()) { "dataDir must not be empty" }
    }

    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)

    /**
     * Current lifecycle state of the daemon.
     *
     * Transitions follow the sequence:
     * [ServiceState.STOPPED] -> [ServiceState.STARTING] -> [ServiceState.RUNNING]
     * and [ServiceState.RUNNING] -> [ServiceState.STOPPING] -> [ServiceState.STOPPED].
     * Any transition may land on [ServiceState.ERROR] on failure.
     */
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _restartRequired = MutableStateFlow(false)

    /** Emits `true` when settings change while the daemon is running. */
    val restartRequired: StateFlow<Boolean> = _restartRequired.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /**
     * Most recent error message from a failed FFI operation.
     *
     * Set when [start] or [stop] throws an [FfiException], cleared
     * on the next successful [start] or [stop]. Returns `null` when
     * no error has occurred or the last operation succeeded.
     */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastStatus = MutableStateFlow<DaemonStatus?>(null)

    /**
     * Most recently fetched daemon health snapshot.
     *
     * Updated each time [pollStatus] completes successfully. Returns
     * `null` until the first successful poll or after a daemon stop.
     */
    val lastStatus: StateFlow<DaemonStatus?> = _lastStatus.asStateFlow()

    private val _keyRejections = MutableSharedFlow<KeyRejectionEvent>(extraBufferCapacity = 1)

    /**
     * Stream of API key rejection events detected during [send] operations.
     *
     * Emitted when the FFI layer returns an error that matches a known
     * authentication or rate-limit pattern. Collectors should use this to
     * surface targeted recovery UI to the user.
     */
    val keyRejections: SharedFlow<KeyRejectionEvent> = _keyRejections.asSharedFlow()

    private val _memoryConflict = MutableStateFlow<MemoryConflict?>(null)

    /**
     * Pending memory conflict that requires user acknowledgment before daemon startup.
     *
     * Non-null when [detectMemoryConflict] found stale artifacts and the daemon
     * is waiting for the user to choose Delete or Keep. Cleared after the user
     * responds via [resolveMemoryConflict].
     */
    val memoryConflict: StateFlow<MemoryConflict?> = _memoryConflict.asStateFlow()

    private val _memoryHealthWarning = MutableStateFlow<String?>(null)

    /**
     * Warning message when the post-startup memory health check fails.
     *
     * Non-null when [checkMemoryHealth] returned [MemoryHealthResult.Unhealthy].
     * Cleared by [dismissMemoryHealthWarning].
     */
    val memoryHealthWarning: StateFlow<String?> = _memoryHealthWarning.asStateFlow()

    /**
     * Deferred that the UI resolves when the user responds to the memory conflict dialog.
     *
     * `true` = delete stale data, `false` = keep. Null when no dialog is pending.
     */
    @Volatile
    private var conflictDeferred: CompletableDeferred<Boolean>? = null

    /**
     * Emits a memory conflict for the UI to display and suspends until resolved.
     *
     * Called by [ZeroClawDaemonService] during startup when stale artifacts are
     * detected. Returns `true` if the user chose to delete stale data.
     *
     * @param conflict The detected conflict descriptor.
     * @return `true` if the user confirmed deletion, `false` to keep.
     */
    internal suspend fun awaitConflictResolution(conflict: MemoryConflict.StaleData): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        conflictDeferred = deferred
        _memoryConflict.value = conflict
        return deferred.await()
    }

    /**
     * Sets a post-startup memory health warning.
     *
     * Called by [ZeroClawDaemonService] when [checkMemoryHealth] returns
     * [MemoryHealthResult.Unhealthy].
     *
     * @param reason Human-readable failure description.
     */
    internal fun setMemoryHealthWarning(reason: String) {
        _memoryHealthWarning.value = reason
    }

    /**
     * Called by the UI when the user responds to the memory conflict dialog.
     *
     * @param shouldDelete True to delete stale files, false to keep them.
     */
    fun resolveMemoryConflict(shouldDelete: Boolean) {
        conflictDeferred?.complete(shouldDelete)
        conflictDeferred = null
        _memoryConflict.value = null
    }

    /**
     * Dismisses the memory health warning banner.
     */
    fun dismissMemoryHealthWarning() {
        _memoryHealthWarning.value = null
    }

    /**
     * Starts the ZeroClaw daemon with the provided configuration.
     *
     * Safe to call from the main thread; the underlying **blocking** FFI
     * call is dispatched to [Dispatchers.IO]. The native call may block
     * for several seconds while the runtime initialises and components
     * spawn. Callers with constrained dispatcher pools should be aware
     * of thread occupation during this window.
     *
     * Updates [serviceState] through the lifecycle:
     * [ServiceState.STARTING] on entry, [ServiceState.RUNNING] on success,
     * or [ServiceState.ERROR] on failure.
     *
     * @param configToml TOML configuration string for the daemon.
     * @param host Gateway bind address (e.g. "127.0.0.1").
     * @param port Gateway bind port.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun start(
        configToml: String,
        host: String,
        port: UShort,
    ) {
        _serviceState.value = ServiceState.STARTING
        try {
            withContext(ioDispatcher) {
                migrateOldWorkspace()
                startDaemon(configToml, dataDir, host, port)
            }
            _lastError.value = null
            _serviceState.value = ServiceState.RUNNING
            _restartRequired.value = false
            eventBridge?.register()
        } catch (e: FfiException) {
            _lastError.value = e.errorDetail()
            _serviceState.value = ServiceState.ERROR
            throw e
        }
    }

    /**
     * Stops the running daemon.
     *
     * Safe to call from the main thread. The underlying **blocking** FFI
     * call is dispatched to [Dispatchers.IO] and waits for all component
     * supervisor tasks to complete, which may take a few seconds.
     *
     * Updates [serviceState] through [ServiceState.STOPPING] on entry,
     * [ServiceState.STOPPED] on success, or [ServiceState.ERROR] on failure.
     *
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun stop() {
        _serviceState.value = ServiceState.STOPPING
        eventBridge?.unregister()
        try {
            withContext(ioDispatcher) { stopDaemon() }
            _lastError.value = null
            _serviceState.value = ServiceState.STOPPED
            _lastStatus.value = null
        } catch (e: FfiException) {
            _lastError.value = e.errorDetail()
            _serviceState.value = ServiceState.ERROR
            throw e
        }
    }

    /**
     * Fetches the current daemon health and updates [lastStatus].
     *
     * Safe to call from the main thread. The underlying **blocking** FFI
     * call is dispatched to [Dispatchers.IO] and typically completes in
     * under 10ms as it only reads in-process health state.
     *
     * @return Parsed [DaemonStatus] snapshot.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun pollStatus(): DaemonStatus {
        val json = withContext(ioDispatcher) { getStatus() }
        val status =
            try {
                parseStatus(json)
            } catch (
                @Suppress("SwallowedException") e: IllegalStateException,
            ) {
                throw FfiException.StateException(
                    e.message ?: "malformed status JSON",
                )
            }
        _lastStatus.value = status
        return status
    }

    /**
     * Sends a message to the daemon gateway and returns the agent response.
     *
     * Safe to call from the main thread. The underlying **blocking** FFI
     * call is dispatched to [Dispatchers.IO] and may block for several
     * seconds while the agent processes the request. Callers with
     * constrained dispatcher pools should be aware of thread occupation.
     *
     * When the FFI layer returns an error matching a known authentication or
     * rate-limit pattern, a [KeyRejectionEvent] is emitted on [keyRejections]
     * before re-throwing the exception.
     *
     * @param message The message text to send.
     * @return The agent's response string.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun send(message: String): String =
        try {
            withContext(ioDispatcher) { sendMessage(message) }
        } catch (e: FfiException) {
            val detail = e.errorDetail()
            val errorType = ApiKeyErrorClassifier.classify(detail)
            if (errorType != null) {
                _keyRejections.tryEmit(
                    KeyRejectionEvent(
                        detail = detail,
                        errorType = errorType,
                    ),
                )
            }
            throw e
        }

    /**
     * Scaffolds the workspace directory with identity template files.
     *
     * Creates the standard `ZeroClaw` workspace structure (5 subdirectories
     * and 8 markdown identity files) at `{dataDir}/workspace`.
     * Existing files are never overwritten (idempotent).
     *
     * Automatically migrates files from the legacy `{dataDir}/zeroclaw/workspace`
     * path used before v0.0.29.
     *
     * Safe to call from the main thread; the underlying **blocking** FFI
     * call is dispatched to [Dispatchers.IO].
     *
     * @param agentName Name for the AI agent (empty defaults to "ZeroClaw").
     * @param userName Name of the human user (empty defaults to "User").
     * @param timezone IANA timezone ID (empty defaults to "UTC").
     * @param communicationStyle Preferred tone (empty uses upstream default).
     * @throws FfiException if the native layer reports an I/O error.
     */
    @Throws(FfiException::class)
    suspend fun ensureWorkspace(
        agentName: String,
        userName: String,
        timezone: String,
        communicationStyle: String,
    ) {
        withContext(ioDispatcher) {
            migrateOldWorkspace()
            scaffoldWorkspace(
                "$dataDir/workspace",
                agentName.take(MAX_IDENTITY_LENGTH),
                userName.take(MAX_IDENTITY_LENGTH),
                timezone.take(MAX_IDENTITY_LENGTH),
                communicationStyle.take(MAX_IDENTITY_LENGTH),
            )
        }
    }

    /**
     * Ensures the workspace directory exists and migrates legacy files.
     *
     * Always creates `{dataDir}/workspace/` so the Rust daemon can reference
     * it even on fresh installs. Then, if the legacy path
     * `{dataDir}/zeroclaw/workspace/` exists (used before v0.0.29), copies
     * any files from there into the new location, preserving user edits.
     * Files that already exist at the new location are not overwritten.
     */
    private fun migrateOldWorkspace() {
        val oldDir = File("$dataDir/zeroclaw/workspace")
        val newDir = File("$dataDir/workspace")
        newDir.mkdirs()
        if (!oldDir.isDirectory) return
        oldDir.listFiles()?.forEach { src ->
            if (src.isFile) {
                val dst = File(newDir, src.name)
                if (!dst.exists()) {
                    src.copyTo(dst)
                }
            }
        }
    }

    /**
     * Detects stale memory backend artifacts in the workspace.
     *
     * Scans the `{dataDir}/workspace` directory for files belonging to a
     * memory backend that is **not** the currently configured one. For
     * example, when [configuredBackend] is `"sqlite"`, any `.md` files
     * in the `memory/` subdirectory are considered stale.
     *
     * @param configuredBackend The active memory backend identifier
     *   (`"sqlite"`, `"markdown"`, or `"none"`).
     * @return [MemoryConflict.StaleData] when leftover files are found,
     *   or [MemoryConflict.None] when the workspace is clean.
     */
    fun detectMemoryConflict(configuredBackend: String): MemoryConflict {
        val workspace = File("$dataDir/workspace")
        if (!workspace.isDirectory) return MemoryConflict.None

        val sqliteFiles = findSqliteFiles(workspace)
        val markdownFiles = findMemoryMarkdownFiles(workspace)

        val staleFiles =
            when (configuredBackend) {
                "sqlite" -> markdownFiles
                "markdown" -> sqliteFiles
                "none" -> sqliteFiles + markdownFiles
                else -> emptyList()
            }

        if (staleFiles.isEmpty()) return MemoryConflict.None

        return MemoryConflict.StaleData(
            currentBackend = configuredBackend,
            staleBackend =
                resolveStaleBackend(
                    configuredBackend,
                    sqliteFiles,
                    markdownFiles,
                ),
            staleFileCount = staleFiles.size,
            staleSizeBytes = staleFiles.sumOf { it.length() },
        )
    }

    /**
     * Determines which stale backend label to use in a [MemoryConflict.StaleData].
     *
     * Returns `"both"` when the `"none"` backend has leftover files from
     * both SQLite and Markdown.
     */
    private fun resolveStaleBackend(
        configuredBackend: String,
        sqliteFiles: List<File>,
        markdownFiles: List<File>,
    ): String =
        when (configuredBackend) {
            "sqlite" -> "markdown"
            "markdown" -> "sqlite"
            "none" ->
                when {
                    sqliteFiles.isNotEmpty() && markdownFiles.isNotEmpty() -> "both"
                    sqliteFiles.isNotEmpty() -> "sqlite"
                    else -> "markdown"
                }
            else -> "unknown"
        }

    /**
     * Deletes stale memory backend files identified by a prior conflict scan.
     *
     * Removes only files belonging to the [conflict]'s [MemoryConflict.StaleData.staleBackend].
     * Parent directories (e.g. `memory/`) are preserved even when emptied.
     *
     * @param conflict The conflict descriptor returned by [detectMemoryConflict].
     */
    fun cleanupStaleMemory(conflict: MemoryConflict.StaleData) {
        val workspace = File("$dataDir/workspace")
        when (conflict.staleBackend) {
            "sqlite" -> findSqliteFiles(workspace).forEach { it.delete() }
            "markdown" -> findMemoryMarkdownFiles(workspace).forEach { it.delete() }
            "both" -> {
                findSqliteFiles(workspace).forEach { it.delete() }
                findMemoryMarkdownFiles(workspace).forEach { it.delete() }
            }
        }
    }

    /**
     * Probes whether the configured memory backend's storage is writable.
     *
     * Writes a small probe file to the target directory, reads it back, and
     * deletes it. Returns [MemoryHealthResult.Healthy] when the round-trip
     * succeeds, or [MemoryHealthResult.Unhealthy] with a diagnostic message
     * on any I/O failure.
     *
     * For the `"none"` backend, storage is always considered healthy because
     * no persistence is required.
     *
     * @param configuredBackend The active memory backend identifier
     *   (`"sqlite"`, `"markdown"`, or `"none"`).
     * @return Health probe result.
     */
    @Suppress("TooGenericExceptionCaught")
    fun checkMemoryHealth(configuredBackend: String): MemoryHealthResult {
        if (configuredBackend == "none") return MemoryHealthResult.Healthy

        return try {
            val targetDir =
                when (configuredBackend) {
                    "markdown" -> File("$dataDir/workspace/memory")
                    else -> File("$dataDir/workspace")
                }
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return MemoryHealthResult.Unhealthy(
                    "Cannot create $configuredBackend storage directory",
                )
            }
            val probe = File(targetDir, ".health_probe")
            try {
                probe.writeText("ok")
                val readBack = probe.readText()
                if (readBack == "ok") {
                    MemoryHealthResult.Healthy
                } else {
                    MemoryHealthResult.Unhealthy(
                        "Read-back mismatch in $configuredBackend storage",
                    )
                }
            } finally {
                probe.delete()
            }
        } catch (e: Exception) {
            MemoryHealthResult.Unhealthy(
                "$configuredBackend storage not writable: ${e.message}",
            )
        }
    }

    /**
     * Finds SQLite database files in a workspace directory.
     *
     * Searches both the workspace root and the `state/` subdirectory for
     * files with `.db`, `.db-wal`, or `.db-shm` extensions.
     *
     * @param workspace The workspace root directory.
     * @return List of matching [File] objects, empty when none are found.
     */
    private fun findSqliteFiles(workspace: File): List<File> {
        val extensions = setOf("db", "db-wal", "db-shm")
        val rootFiles =
            workspace
                .listFiles()
                ?.filter { it.isFile && it.extension in extensions }
                .orEmpty()
        val stateDir = File(workspace, "state")
        val stateFiles =
            stateDir
                .listFiles()
                ?.filter { it.isFile && it.extension in extensions }
                .orEmpty()
        return rootFiles + stateFiles
    }

    /**
     * Finds Markdown memory log files in the workspace's `memory/` subdirectory.
     *
     * @param workspace The workspace root directory.
     * @return List of `.md` files inside `{workspace}/memory/`,
     *   empty when the directory does not exist.
     */
    private fun findMemoryMarkdownFiles(workspace: File): List<File> {
        val memoryDir = File(workspace, "memory")
        if (!memoryDir.isDirectory) return emptyList()
        return memoryDir
            .listFiles()
            ?.filter { it.isFile && it.extension == "md" }
            .orEmpty()
    }

    /**
     * Marks that a restart is required to apply settings changes.
     *
     * Only sets the flag when the daemon is currently [ServiceState.RUNNING].
     * Ignored in all other states.
     */
    fun markRestartRequired() {
        if (_serviceState.value == ServiceState.RUNNING) {
            _restartRequired.value = true
        }
    }

    /**
     * Stops and re-starts the daemon with a fresh configuration.
     *
     * @param configToml New TOML configuration string.
     * @param host Gateway host address.
     * @param port Gateway port.
     * @throws FfiException If either stop or start fails.
     */
    @Throws(FfiException::class)
    suspend fun restart(
        configToml: String,
        host: String,
        port: UShort,
    ) {
        stop()
        start(configToml, host, port)
    }

    /**
     * Parses a raw JSON health snapshot into a [DaemonStatus].
     *
     * This is the sole JSON schema interpretation point for daemon health
     * data. All other code should consume [DaemonStatus] rather than
     * parsing the JSON directly.
     *
     * @param json Raw JSON string from [com.zeroclaw.ffi.getStatus].
     * @return Parsed [DaemonStatus] instance.
     * @throws IllegalStateException if the JSON is malformed.
     */
    private fun parseStatus(json: String): DaemonStatus {
        try {
            val obj = JSONObject(json)
            val componentsObj = obj.optJSONObject("components")
            val components = mutableMapOf<String, ComponentStatus>()
            if (componentsObj != null) {
                for (key in componentsObj.keys()) {
                    val comp = componentsObj.optJSONObject(key)
                    components[key] =
                        ComponentStatus(
                            name = key,
                            status =
                                comp?.optString("status", "unknown")
                                    ?: "unknown",
                        )
                }
            }
            return DaemonStatus(
                running = obj.optBoolean("daemon_running", false),
                uptimeSeconds = obj.optLong("uptime_seconds", 0),
                components = components,
            )
        } catch (e: org.json.JSONException) {
            throw IllegalStateException(
                "Native layer returned invalid status JSON",
                e,
            )
        }
    }

    /** Constants for [DaemonServiceBridge]. */
    companion object {
        /** Maximum length for workspace identity strings passed to FFI. */
        private const val MAX_IDENTITY_LENGTH = 100
    }
}

/**
 * Extracts the human-readable error detail from any [FfiException] subtype.
 *
 * UniFFI-generated exception subclasses override [Throwable.message] with
 * a formatted string that includes field names (e.g. `"detail=some error"`).
 * This function accesses the `detail` property directly for a clean message.
 *
 * @receiver the [FfiException] to extract the detail from.
 * @return the error detail string.
 */
private fun FfiException.errorDetail(): String =
    when (this) {
        is FfiException.ConfigException -> detail
        is FfiException.StateException -> detail
        is FfiException.SpawnException -> detail
        is FfiException.ShutdownException -> detail
        is FfiException.InternalPanic -> detail
        is FfiException.StateCorrupted -> detail
    }
