/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.model.ProcessedImage
import com.zeroclaw.android.model.ProviderAuthType
import com.zeroclaw.android.model.RefreshCommand
import com.zeroclaw.android.model.TerminalEntry
import com.zeroclaw.android.util.ErrorSanitizer
import com.zeroclaw.android.util.ImageProcessor
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiSessionListener
import com.zeroclaw.ffi.evalRepl
import com.zeroclaw.ffi.getVersion
import com.zeroclaw.ffi.sessionCancel
import com.zeroclaw.ffi.sessionDestroy
import com.zeroclaw.ffi.sessionSend
import com.zeroclaw.ffi.sessionStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the terminal REPL screen.
 *
 * Routes user input through the [CommandRegistry] to classify it as a Rhai
 * expression, a local action, or a chat message, then dispatches accordingly.
 * Rhai expressions are evaluated against the daemon via FFI on
 * [Dispatchers.IO]. All inputs and outputs are persisted through the
 * [TerminalEntryRepository][com.zeroclaw.android.data.repository.TerminalEntryRepository]
 * so history survives navigation and app restarts.
 *
 * Supports image attachments via the photo picker, with vision requests
 * routed through the `send_vision` Rhai function.
 *
 * @param application Application context for accessing repositories and bridges.
 */
class TerminalViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as ZeroClawApplication
    private val repository = app.terminalEntryRepository
    private val logRepository = app.logRepository
    private val settingsRepository = app.settingsRepository
    private val agentRepository = app.agentRepository
    private val apiKeyRepository = app.apiKeyRepository

    private val cachedSettings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val loadingState = MutableStateFlow(false)
    private val pendingImagesState = MutableStateFlow<List<ProcessedImage>>(emptyList())
    private val processingImagesState = MutableStateFlow(false)
    private val _streamingState = MutableStateFlow(StreamingState.idle())
    private val _history = MutableStateFlow<List<String>>(emptyList())
    private val _historyIndex = MutableStateFlow(NO_HISTORY_SELECTION)

    /** Observable terminal state combining persisted entries with transient UI state. */
    val state: StateFlow<TerminalState> =
        combine(
            repository.entries,
            loadingState,
            pendingImagesState,
            processingImagesState,
        ) { entries, loading, images, processingImages ->
            TerminalState(
                blocks = entries.map(::toBlock),
                isLoading = loading,
                pendingImages = images,
                isProcessingImages = processingImages,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TerminalState())

    /** Previous input lines for history navigation, newest last. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /** Current position in the input history, or -1 when not navigating. */
    val historyIndex: StateFlow<Int> = _historyIndex.asStateFlow()

    /** Observable streaming state for the live agent session. */
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    init {
        addWelcomeMessage()
        initAgentSession()
    }

    /**
     * Initialises the live agent session on a background thread.
     *
     * Calls [sessionStart] on [Dispatchers.IO]. Failures are logged but
     * do not prevent the terminal from operating in Rhai-only mode.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun initAgentSession() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    sessionStart()
                }
            } catch (e: Exception) {
                logRepository.append(
                    LogSeverity.WARN,
                    TAG,
                    "Agent session init failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Tears down the agent session when the ViewModel is destroyed.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun onCleared() {
        super.onCleared()
        try {
            sessionDestroy()
        } catch (_: Exception) {
        }
    }

    /**
     * Submits user input for processing.
     *
     * Parses the input through [CommandRegistry.parseAndTranslate] and
     * dispatches based on the result type:
     * - [CommandResult.RhaiExpression]: persists the input, evaluates
     *   via FFI, and persists the response or error.
     * - [CommandResult.LocalAction]: handles "help" and "clear" locally.
     * - [CommandResult.ChatMessage]: wraps in a `send()` Rhai call,
     *   or `send_vision()` when images are attached.
     *
     * @param text The raw text entered by the user.
     */
    fun submitInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && pendingImagesState.value.isEmpty()) return

        appendToHistory(trimmed)
        _historyIndex.value = NO_HISTORY_SELECTION

        val result = CommandRegistry.parseAndTranslate(trimmed)
        when (result) {
            is CommandResult.RhaiExpression -> executeRhai(trimmed, result.expression)
            is CommandResult.LocalAction -> handleLocalAction(result.action)
            is CommandResult.ChatMessage -> executeChatMessage(trimmed, result.text)
        }
    }

    /**
     * Navigates backward (older) through input history.
     *
     * @return The history entry at the new position, or `null` if history is empty.
     */
    fun historyUp(): String? {
        val items = _history.value
        if (items.isEmpty()) return null
        val current = _historyIndex.value
        val newIndex =
            if (current == NO_HISTORY_SELECTION) {
                items.lastIndex
            } else {
                (current - 1).coerceAtLeast(0)
            }
        _historyIndex.value = newIndex
        return items[newIndex]
    }

    /**
     * Navigates forward (newer) through input history.
     *
     * @return The history entry at the new position, or `null` if past the newest entry.
     */
    fun historyDown(): String? {
        val items = _history.value
        val current = _historyIndex.value
        if (current == NO_HISTORY_SELECTION || items.isEmpty()) return null
        val newIndex = current + 1
        if (newIndex > items.lastIndex) {
            _historyIndex.value = NO_HISTORY_SELECTION
            return null
        }
        _historyIndex.value = newIndex
        return items[newIndex]
    }

    /**
     * Processes and stages images from the photo picker.
     *
     * Runs [ImageProcessor.process] on [Dispatchers.IO] to downscale,
     * compress, and base64-encode the selected images. Results are appended
     * to the pending images list, capped at [MAX_IMAGES].
     *
     * @param uris Content URIs returned by the photo picker.
     */
    fun attachImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            processingImagesState.value = true
            try {
                val contentResolver = app.contentResolver
                val processed = ImageProcessor.process(contentResolver, uris)
                val current = pendingImagesState.value
                pendingImagesState.value = (current + processed).take(MAX_IMAGES)
            } finally {
                processingImagesState.value = false
            }
        }
    }

    /**
     * Removes a pending image at the given index.
     *
     * @param index Zero-based index into the pending images list.
     */
    fun removeImage(index: Int) {
        val current = pendingImagesState.value
        if (index in current.indices) {
            pendingImagesState.value = current.toMutableList().apply { removeAt(index) }
        }
    }

    /**
     * Evaluates a Rhai expression via FFI and persists the result.
     *
     * The input is immediately persisted as an "input" entry. The expression
     * is then evaluated on [Dispatchers.IO]. Successful results are persisted
     * as "response" entries; failures are persisted as "error" entries.
     *
     * @param displayText The original user input shown in the scrollback.
     * @param expression The Rhai expression to evaluate.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun executeRhai(
        displayText: String,
        expression: String,
    ) {
        loadingState.value = true
        viewModelScope.launch {
            repository.append(content = displayText, entryType = ENTRY_TYPE_INPUT)
            try {
                val rawResult =
                    withContext(Dispatchers.IO) {
                        evalRepl(expression)
                    }
                val cleaned = stripToolCallTags(rawResult)
                val result =
                    if (cachedSettings.value.stripThinkingTags) {
                        stripThinkingTags(cleaned)
                    } else {
                        cleaned
                    }
                val displayResult =
                    result.ifBlank {
                        rawResult.trim().ifBlank { EMPTY_RESPONSE_FALLBACK }
                    }
                repository.append(content = displayResult, entryType = ENTRY_TYPE_RESPONSE)
                emitRefreshIfNeeded(expression)
            } catch (e: FfiException) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                logRepository.append(LogSeverity.ERROR, TAG, "REPL eval failed: $sanitized")
                repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
            } catch (e: Exception) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                logRepository.append(LogSeverity.ERROR, TAG, "REPL eval failed: $sanitized")
                repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
            } finally {
                loadingState.value = false
            }
        }
    }

    /**
     * Dispatches a chat message through the agent session or Rhai bridge.
     *
     * Plain text messages are routed through the live agent session via
     * [executeAgentTurn]. Vision requests (with attached images) are still
     * evaluated through the Rhai `send_vision()` bridge because
     * `session_send_vision` is not yet implemented on the FFI side.
     *
     * @param displayText The original user input shown in the scrollback.
     * @param escapedText The Rhai-escaped message text.
     */
    private fun executeChatMessage(
        displayText: String,
        escapedText: String,
    ) {
        val images = pendingImagesState.value
        pendingImagesState.value = emptyList()

        val inputImageUris = images.map { it.originalUri }
        viewModelScope.launch {
            repository.append(
                content = displayText,
                entryType = ENTRY_TYPE_INPUT,
                imageUris = inputImageUris,
            )

            if (!isChatProviderConfigured()) {
                repository.append(
                    content = NO_PROVIDER_WARNING,
                    entryType = ENTRY_TYPE_SYSTEM,
                )
                return@launch
            }

            if (images.isNotEmpty()) {
                val expression = buildVisionExpression(escapedText, images)
                loadingState.value = true
                executeRhaiExpression(expression)
            } else {
                executeAgentTurn(displayText)
            }
        }
    }

    /**
     * Executes a user message through the live agent session.
     *
     * Sends the message to the Rust-side agent loop via [sessionSend].
     * Progress events are delivered to [_streamingState] through the
     * [KotlinSessionListener] callback. On completion, the full response
     * is persisted to the terminal repository.
     *
     * The caller is responsible for persisting the user input entry and
     * verifying that a chat provider is configured before calling this
     * method. See [executeChatMessage] for the call-site contract.
     *
     * @param message The message text to send to the agent.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun executeAgentTurn(message: String) {
        viewModelScope.launch {
            _streamingState.update { StreamingState(phase = StreamingPhase.THINKING) }

            try {
                withContext(Dispatchers.IO) {
                    sessionSend(message, KotlinSessionListener())
                }
            } catch (e: FfiException) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                _streamingState.update {
                    StreamingState(phase = StreamingPhase.ERROR, errorMessage = sanitized)
                }
                logRepository.append(LogSeverity.ERROR, TAG, "Agent turn failed: $sanitized")
                repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
            } catch (e: Exception) {
                val sanitized = ErrorSanitizer.sanitizeForUi(e)
                _streamingState.update {
                    StreamingState(phase = StreamingPhase.ERROR, errorMessage = sanitized)
                }
                logRepository.append(LogSeverity.ERROR, TAG, "Agent turn failed: $sanitized")
                repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
            }
        }
    }

    /**
     * Cancels the currently running agent turn.
     *
     * Signals the Rust-side cancellation token. The [KotlinSessionListener]
     * will receive [FfiSessionListener.onCancelled] and transition the
     * streaming state to [StreamingPhase.CANCELLED].
     */
    @Suppress("TooGenericExceptionCaught")
    fun cancelAgentTurn() {
        try {
            sessionCancel()
        } catch (_: Exception) {
        }
    }

    /**
     * Checks whether the first enabled agent has a usable chat provider.
     *
     * Mirrors the `resolveEffectiveDefaults` pattern from
     * [ZeroClawDaemonService][com.zeroclaw.android.service.ZeroClawDaemonService].
     * Providers with [ProviderAuthType.URL_ONLY], [ProviderAuthType.URL_AND_OPTIONAL_KEY],
     * or [ProviderAuthType.NONE] are considered configured without an API key.
     * Providers requiring a key ([ProviderAuthType.API_KEY_ONLY],
     * [ProviderAuthType.API_KEY_OR_OAUTH]) are only considered configured when
     * a non-blank key exists in the repository.
     *
     * @return True if a chat provider is ready for use.
     */
    private suspend fun isChatProviderConfigured(): Boolean {
        val agents = agentRepository.agents.first()
        val primary =
            agents.firstOrNull {
                it.isEnabled && it.provider.isNotBlank() && it.modelName.isNotBlank()
            } ?: return false

        val providerInfo = ProviderRegistry.findById(primary.provider) ?: return false
        return when (providerInfo.authType) {
            ProviderAuthType.URL_ONLY,
            ProviderAuthType.URL_AND_OPTIONAL_KEY,
            ProviderAuthType.NONE,
            -> true
            ProviderAuthType.API_KEY_ONLY,
            ProviderAuthType.API_KEY_OR_OAUTH,
            -> {
                val key = apiKeyRepository.getByProvider(primary.provider)
                key != null && key.key.isNotBlank()
            }
        }
    }

    /**
     * Handles a local action that does not require FFI evaluation.
     *
     * @param action The action identifier (e.g. "help", "clear").
     */
    private fun handleLocalAction(action: String) {
        when (action) {
            "help" -> showHelp()
            "clear" -> clearTerminal()
        }
    }

    /**
     * Generates and persists the help text listing all available commands.
     */
    private fun showHelp() {
        viewModelScope.launch {
            repository.append(content = "/help", entryType = ENTRY_TYPE_INPUT)
            val helpText =
                buildString {
                    appendLine("Available commands:")
                    appendLine()
                    for (command in CommandRegistry.commands) {
                        val usage =
                            if (command.usage.isNotEmpty()) {
                                " ${command.usage}"
                            } else {
                                ""
                            }
                        appendLine("  /${command.name}$usage")
                        appendLine("    ${command.description}")
                    }
                    appendLine()
                    append("Any other input is sent as a chat message.")
                }
            repository.append(content = helpText, entryType = ENTRY_TYPE_SYSTEM)
        }
    }

    /**
     * Clears all terminal history and adds a confirmation message.
     */
    private fun clearTerminal() {
        repository.clear()
        viewModelScope.launch {
            repository.append(content = CLEAR_CONFIRMATION, entryType = ENTRY_TYPE_SYSTEM)
        }
    }

    /**
     * Evaluates a Rhai expression and persists the result.
     *
     * Shared execution path for chat messages and vision requests after
     * the input has already been persisted.
     *
     * @param expression The Rhai expression to evaluate.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRhaiExpression(expression: String) {
        try {
            val rawResult =
                withContext(Dispatchers.IO) {
                    evalRepl(expression)
                }
            val cleaned = stripToolCallTags(rawResult)
            val result =
                if (cachedSettings.value.stripThinkingTags) {
                    stripThinkingTags(cleaned)
                } else {
                    cleaned
                }
            val displayResult =
                result.ifBlank {
                    rawResult.trim().ifBlank { EMPTY_RESPONSE_FALLBACK }
                }
            repository.append(content = displayResult, entryType = ENTRY_TYPE_RESPONSE)
            emitRefreshIfNeeded(expression)
        } catch (e: FfiException) {
            val sanitized = ErrorSanitizer.sanitizeForUi(e)
            logRepository.append(LogSeverity.ERROR, TAG, "REPL eval failed: $sanitized")
            repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
        } catch (e: Exception) {
            val sanitized = ErrorSanitizer.sanitizeForUi(e)
            logRepository.append(LogSeverity.ERROR, TAG, "REPL eval failed: $sanitized")
            repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
        } finally {
            loadingState.value = false
        }
    }

    /**
     * Inserts the welcome banner as the first terminal entry.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun addWelcomeMessage() {
        viewModelScope.launch {
            val version =
                try {
                    getVersion()
                } catch (e: Exception) {
                    logRepository.append(
                        LogSeverity.WARN,
                        TAG,
                        "Failed to read version: ${e.message}",
                    )
                    "unknown"
                }
            val banner =
                if (isChatProviderConfigured()) {
                    "ZeroClaw Terminal v$version \u2014 Type /help for commands"
                } else {
                    "ZeroClaw Terminal v$version \u2014 Admin Console " +
                        "(no chat provider) \u2014 Type /help for commands"
                }
            repository.append(content = banner, entryType = ENTRY_TYPE_SYSTEM)
        }
    }

    /**
     * Appends a non-blank input to the history buffer.
     *
     * Duplicate consecutive entries are suppressed to keep the history clean.
     *
     * @param text The input text to record.
     */
    private fun appendToHistory(text: String) {
        if (text.isBlank()) return
        val current = _history.value
        if (current.lastOrNull() == text) return
        _history.value = current + text
    }

    /**
     * Builds a `send_vision` Rhai expression from text and processed images.
     *
     * Constructs Rhai array literals for the base64-encoded image data and
     * MIME types, then wraps them in a `send_vision(text, images, mimes)` call.
     *
     * @param escapedText Rhai-escaped message text.
     * @param images Processed images to include.
     * @return A complete Rhai expression string.
     */
    private fun buildVisionExpression(
        escapedText: String,
        images: List<ProcessedImage>,
    ): String {
        val imageArray = images.joinToString(", ") { "\"${it.base64Data}\"" }
        val mimeArray = images.joinToString(", ") { "\"${it.mimeType}\"" }
        return "send_vision(\"$escapedText\", [$imageArray], [$mimeArray])"
    }

    /**
     * Emits a [RefreshCommand] to trigger immediate data refresh in other
     * ViewModels after a successful mutating REPL command.
     *
     * @param expression The Rhai expression that was successfully evaluated.
     */
    private fun emitRefreshIfNeeded(expression: String) {
        val command =
            when {
                expression.startsWith("cron_add(") ||
                    expression.startsWith("cron_oneshot(") ||
                    expression.startsWith("cron_remove(") ||
                    expression.startsWith("cron_pause(") ||
                    expression.startsWith("cron_resume(") -> RefreshCommand.Cron
                expression.startsWith("send(") ||
                    expression.startsWith("send_vision(") -> RefreshCommand.Cost
                expression.startsWith("skill_install(") ||
                    expression.startsWith("skill_remove(") -> RefreshCommand.Health
                else -> null
            }
        if (command != null) {
            app.refreshCommands.tryEmit(command)
        }
    }

    /**
     * Callback adapter that translates FFI session events into
     * [StreamingState] updates and terminal repository entries.
     *
     * All methods are called from the tokio runtime thread. State updates
     * use [MutableStateFlow.update] which is thread-safe.
     */
    private inner class KotlinSessionListener : FfiSessionListener {
        override fun onThinking(text: String) {
            _streamingState.update { current ->
                current.copy(
                    phase = StreamingPhase.THINKING,
                    thinkingText = current.thinkingText + text,
                )
            }
        }

        override fun onResponseChunk(text: String) {
            _streamingState.update { current ->
                current.copy(
                    phase = StreamingPhase.RESPONDING,
                    responseText = current.responseText + text,
                )
            }
        }

        override fun onToolStart(
            name: String,
            argumentsHint: String,
        ) {
            _streamingState.update { current ->
                current.copy(
                    phase = StreamingPhase.TOOL_EXECUTING,
                    activeTools = current.activeTools + ToolProgress(name, argumentsHint),
                )
            }
        }

        override fun onToolResult(
            name: String,
            success: Boolean,
            durationSecs: ULong,
        ) {
            _streamingState.update { current ->
                current.copy(
                    activeTools = current.activeTools.filter { it.name != name },
                    toolResults =
                        current.toolResults +
                            ToolResultEntry(
                                name = name,
                                success = success,
                                durationSecs = durationSecs.toLong(),
                            ),
                )
            }
        }

        override fun onToolOutput(
            name: String,
            output: String,
        ) {
            _streamingState.update { current ->
                val updated =
                    current.toolResults.map { entry ->
                        if (entry.name == name && entry.output.isEmpty()) {
                            entry.copy(output = output)
                        } else {
                            entry
                        }
                    }
                current.copy(toolResults = updated)
            }
        }

        override fun onProgress(message: String) {
            _streamingState.update { current ->
                current.copy(progressMessage = message)
            }
        }

        override fun onCompaction(summary: String) {
            _streamingState.update { current ->
                current.copy(phase = StreamingPhase.COMPACTING, progressMessage = summary)
            }
        }

        override fun onComplete(fullResponse: String) {
            val cleaned = stripToolCallTags(fullResponse)
            val stripped =
                if (cachedSettings.value.stripThinkingTags) {
                    stripThinkingTags(cleaned)
                } else {
                    cleaned
                }
            val display = stripped.ifBlank { EMPTY_RESPONSE_FALLBACK }

            viewModelScope.launch {
                repository.append(content = display, entryType = ENTRY_TYPE_RESPONSE)
            }

            _streamingState.update { StreamingState(phase = StreamingPhase.COMPLETE) }

            app.refreshCommands.tryEmit(RefreshCommand.Cost)
        }

        override fun onError(error: String) {
            val sanitized = ErrorSanitizer.sanitizeMessage(error)

            viewModelScope.launch {
                repository.append(content = sanitized, entryType = ENTRY_TYPE_ERROR)
                logRepository.append(LogSeverity.ERROR, TAG, "Agent session error: $sanitized")
            }

            _streamingState.update {
                StreamingState(phase = StreamingPhase.ERROR, errorMessage = sanitized)
            }
        }

        override fun onCancelled() {
            viewModelScope.launch {
                repository.append(
                    content = "Request cancelled.",
                    entryType = ENTRY_TYPE_SYSTEM,
                )
            }

            _streamingState.update {
                StreamingState(phase = StreamingPhase.CANCELLED)
            }
        }
    }

    /** Constants for [TerminalViewModel]. */
    companion object {
        private const val TAG = "Terminal"

        /** Timeout in milliseconds before upstream Flow collection stops. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Maximum number of images per message (matches FFI-side limit). */
        private const val MAX_IMAGES = 5

        /** Sentinel value indicating no history selection is active. */
        private const val NO_HISTORY_SELECTION = -1

        /** Entry type constant for user input entries. */
        private const val ENTRY_TYPE_INPUT = "input"

        /** Entry type constant for daemon response entries. */
        private const val ENTRY_TYPE_RESPONSE = "response"

        /** Entry type constant for error entries. */
        private const val ENTRY_TYPE_ERROR = "error"

        /** Entry type constant for system message entries. */
        private const val ENTRY_TYPE_SYSTEM = "system"

        /** Displayed when the model response is empty after stripping markup. */
        private const val EMPTY_RESPONSE_FALLBACK =
            "The model did not generate a text response."

        /** Confirmation message shown after clearing the terminal. */
        private const val CLEAR_CONFIRMATION = "Terminal cleared."

        /** Warning shown when user sends a chat message without a configured provider. */
        private const val NO_PROVIDER_WARNING =
            "No chat provider configured \u2014 use /help to see admin commands, " +
                "or add a provider in Settings > API Keys."

        /** Pattern matching common chain-of-thought tag variants across models. */
        private val THINKING_TAG_REGEX =
            Regex(
                "<(?:think|thinking)>[\\s\\S]*?</(?:think|thinking)>",
                RegexOption.IGNORE_CASE,
            )

        /**
         * Pattern matching tool-call markup leaked by models that support
         * function calling.
         *
         * Matches self-closing tags (`<tool_call name="..." args="..."/>`),
         * complete blocks (`<tool_call>...</tool_call>`), and unclosed tags
         * through end-of-string. Also covers the `<function_call>` variant
         * used by some models.
         */
        private val TOOL_CALL_TAG_REGEX =
            Regex(
                "<(?:tool_call|function_call)\\b[\\s\\S]*?/>" +
                    "|<(?:tool_call|function_call)>[\\s\\S]*?</(?:tool_call|function_call)>" +
                    "|<(?:tool_call|function_call)[\\s\\S]*$",
                RegexOption.IGNORE_CASE,
            )

        /**
         * Removes chain-of-thought thinking tags from a model response.
         *
         * Strips `<think>...</think>` and `<thinking>...</thinking>` blocks
         * emitted by reasoning models (Gemma, DeepSeek-R1, QwQ, etc.).
         *
         * @param text Raw model response.
         * @return Response with thinking blocks removed and whitespace trimmed.
         */
        fun stripThinkingTags(text: String): String = text.replace(THINKING_TAG_REGEX, "").trim()

        /**
         * Removes tool-call markup from a model response.
         *
         * Some models (notably Qwen) emit raw `<tool_call>` tags in their
         * text content when they attempt a function call with no tools
         * available or produce a malformed tool invocation.
         *
         * @param text Raw model response.
         * @return Response with tool-call blocks removed and whitespace trimmed.
         */
        fun stripToolCallTags(text: String): String = text.replace(TOOL_CALL_TAG_REGEX, "").trim()

        /**
         * Maps a persisted [TerminalEntry] to a display [TerminalBlock].
         *
         * Response entries whose content starts with `{` or `[` are
         * classified as [TerminalBlock.Structured] for JSON rendering.
         *
         * @param entry The persisted terminal entry.
         * @return The corresponding display block.
         */
        fun toBlock(entry: TerminalEntry): TerminalBlock =
            when (entry.entryType) {
                ENTRY_TYPE_INPUT ->
                    TerminalBlock.Input(
                        id = entry.id,
                        timestamp = entry.timestamp,
                        text = entry.content,
                        imageNames =
                            entry.imageUris.map { uri ->
                                uri.substringAfterLast('/')
                            },
                    )
                ENTRY_TYPE_RESPONSE -> {
                    val trimmed = entry.content.trimStart()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        TerminalBlock.Structured(
                            id = entry.id,
                            timestamp = entry.timestamp,
                            json = entry.content,
                        )
                    } else {
                        TerminalBlock.Response(
                            id = entry.id,
                            timestamp = entry.timestamp,
                            content = entry.content,
                        )
                    }
                }
                ENTRY_TYPE_ERROR ->
                    TerminalBlock.Error(
                        id = entry.id,
                        timestamp = entry.timestamp,
                        message = entry.content,
                    )
                else ->
                    TerminalBlock.System(
                        id = entry.id,
                        timestamp = entry.timestamp,
                        text = entry.content,
                    )
            }
    }
}

/**
 * Immutable snapshot of the terminal REPL screen state.
 *
 * @property blocks Ordered list of terminal blocks for the scrollback buffer.
 * @property isLoading True while waiting for an FFI response.
 * @property pendingImages Images staged for the next message.
 * @property isProcessingImages True while images are being downscaled and encoded.
 */
data class TerminalState(
    val blocks: List<TerminalBlock> = emptyList(),
    val isLoading: Boolean = false,
    val pendingImages: List<ProcessedImage> = emptyList(),
    val isProcessingImages: Boolean = false,
)
