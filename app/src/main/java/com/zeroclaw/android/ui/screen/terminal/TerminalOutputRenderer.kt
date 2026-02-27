/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

@file:Suppress("TooManyFunctions")

package com.zeroclaw.android.ui.screen.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.TerminalTypography
import org.json.JSONArray
import org.json.JSONObject

/** Horizontal padding inside each terminal block. */
private const val BLOCK_HORIZONTAL_PADDING_DP = 12

/** Vertical padding inside each terminal block. */
private const val BLOCK_VERTICAL_PADDING_DP = 8

/** Corner radius for structured output containers. */
private const val STRUCTURED_CORNER_DP = 8

/** Border width for structured output containers. */
private const val STRUCTURED_BORDER_DP = 1

/** Indentation spaces for pretty-printed JSON. */
private const val JSON_INDENT_SPACES = 2

/** JSON key for daemon running status detection. */
private const val KEY_DAEMON_RUNNING = "daemon_running"

/** JSON key for session cost detection. */
private const val KEY_SESSION_COST = "session_cost_usd"

/**
 * Renders a single [TerminalBlock] in the terminal scrollback.
 *
 * Each block variant has its own visual style: input lines show a
 * prompt prefix, responses use plain monospace text, structured output
 * renders formatted JSON, errors are highlighted in red, and system
 * messages appear dimmed. All blocks support long-press to copy via
 * [onCopy] and expose merged accessibility semantics.
 *
 * @param block The terminal block to render.
 * @param onCopy Callback invoked with the copyable text on long-press.
 * @param modifier Modifier applied to the block container.
 */
@Composable
fun TerminalBlockItem(
    block: TerminalBlock,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is TerminalBlock.Input -> InputBlock(block, onCopy, modifier)
        is TerminalBlock.Response -> ResponseBlock(block, onCopy, modifier)
        is TerminalBlock.Structured -> StructuredBlock(block, onCopy, modifier)
        is TerminalBlock.Error -> ErrorBlock(block, onCopy, modifier)
        is TerminalBlock.System -> SystemBlock(block, onCopy, modifier)
    }
}

/**
 * Renders a user input block with a `> ` prompt prefix.
 *
 * @param block The input block to render.
 * @param onCopy Callback invoked with the input text on long-press.
 * @param modifier Modifier applied to the block container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputBlock(
    block: TerminalBlock.Input,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCommand = block.text.startsWith("/")
    val description = if (isCommand) "Command: ${block.text}" else "Message: ${block.text}"

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(block.text) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = description
                }.padding(
                    horizontal = BLOCK_HORIZONTAL_PADDING_DP.dp,
                    vertical = BLOCK_VERTICAL_PADDING_DP.dp,
                ),
    ) {
        val annotatedPrompt =
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append("> ")
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(block.text)
                }
            }
        Text(
            text = annotatedPrompt,
            style = TerminalTypography.bodyMedium,
        )
        for (imageName in block.imageNames) {
            Text(
                text = "  [image: $imageName]",
                style = TerminalTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders an agent response block as plain monospace text.
 *
 * @param block The response block to render.
 * @param onCopy Callback invoked with the response content on long-press.
 * @param modifier Modifier applied to the block container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResponseBlock(
    block: TerminalBlock.Response,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = block.content,
        style = TerminalTypography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(block.content) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = block.content
                }.padding(
                    horizontal = BLOCK_HORIZONTAL_PADDING_DP.dp,
                    vertical = BLOCK_VERTICAL_PADDING_DP.dp,
                ),
    )
}

/**
 * Renders a structured JSON output block with pattern-detected formatting.
 *
 * Detects common response patterns (status, cost summary, arrays) and
 * renders them in a human-readable format. Falls back to pretty-printed
 * JSON for unrecognised structures.
 *
 * @param block The structured block to render.
 * @param onCopy Callback invoked with the raw JSON on long-press.
 * @param modifier Modifier applied to the block container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StructuredBlock(
    block: TerminalBlock.Structured,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val formattedContent = remember(block.json) { formatStructuredJson(block.json) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(STRUCTURED_CORNER_DP.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = STRUCTURED_BORDER_DP.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(STRUCTURED_CORNER_DP.dp),
                ).combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(block.json) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = formattedContent
                },
    ) {
        Text(
            text = formattedContent,
            style = TerminalTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = BLOCK_HORIZONTAL_PADDING_DP.dp,
                    vertical = BLOCK_VERTICAL_PADDING_DP.dp,
                ),
        )
    }
}

/**
 * Renders an error block with red text and an "Error: " prefix.
 *
 * @param block The error block to render.
 * @param onCopy Callback invoked with the error message on long-press.
 * @param modifier Modifier applied to the block container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ErrorBlock(
    block: TerminalBlock.Error,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Error: ${block.message}",
        style = TerminalTypography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(block.message) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = "Error: ${block.message}"
                }.padding(
                    horizontal = BLOCK_HORIZONTAL_PADDING_DP.dp,
                    vertical = BLOCK_VERTICAL_PADDING_DP.dp,
                ),
    )
}

/**
 * Renders a system message block in dimmed outline colour.
 *
 * @param block The system block to render.
 * @param onCopy Callback invoked with the system message text on long-press.
 * @param modifier Modifier applied to the block container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemBlock(
    block: TerminalBlock.System,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = block.text,
        style = TerminalTypography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(block.text) },
                ).semantics(mergeDescendants = true) {
                    contentDescription = block.text
                }.padding(
                    horizontal = BLOCK_HORIZONTAL_PADDING_DP.dp,
                    vertical = BLOCK_VERTICAL_PADDING_DP.dp,
                ),
    )
}

/**
 * Formats a JSON string into a human-readable representation.
 *
 * Detects common response patterns:
 * - Objects with `daemon_running` field: status table with indicators.
 * - Objects with `session_cost_usd` field: cost summary.
 * - JSON arrays of objects: numbered list of entries.
 * - Fallback: pretty-printed JSON.
 *
 * @param json The raw JSON string to format.
 * @return A human-readable text representation.
 */
private fun formatStructuredJson(json: String): String {
    val trimmed = json.trim()
    if (trimmed.startsWith("{")) {
        return formatJsonObject(trimmed)
    }
    if (trimmed.startsWith("[")) {
        return formatJsonArray(trimmed)
    }
    return trimmed
}

/**
 * Formats a JSON object string based on detected field patterns.
 *
 * @param json A JSON object string.
 * @return Formatted text representation.
 */
private fun formatJsonObject(json: String): String {
    val obj =
        runCatching { JSONObject(json) }.getOrNull()
            ?: return json

    if (obj.has(KEY_DAEMON_RUNNING)) {
        return formatStatusObject(obj)
    }
    if (obj.has(KEY_SESSION_COST)) {
        return formatCostObject(obj)
    }
    return formatGenericObject(obj)
}

/**
 * Formats a daemon status JSON object with running indicators.
 *
 * @param obj The parsed JSON object containing status fields.
 * @return A multi-line status summary.
 */
private fun formatStatusObject(obj: JSONObject): String =
    buildString {
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val value = obj.get(key)
            val label = key.replace("_", " ")
            val indicator = if (value == true) "\u25CF" else "\u25CB"
            if (value is Boolean) {
                appendLine("$indicator $label")
            } else {
                appendLine("  $label: $value")
            }
        }
    }.trimEnd()

/**
 * Formats a cost summary JSON object.
 *
 * @param obj The parsed JSON object containing cost fields.
 * @return A multi-line cost summary.
 */
private fun formatCostObject(obj: JSONObject): String =
    buildString {
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val value = obj.get(key)
            val label = key.replace("_", " ")
            appendLine("$label: $value")
        }
    }.trimEnd()

/**
 * Formats a JSON array, rendering each element as a numbered entry.
 *
 * @param json A JSON array string.
 * @return A numbered list of entries, or pretty-printed JSON on parse failure.
 */
private fun formatJsonArray(json: String): String {
    val arr =
        runCatching { JSONArray(json) }.getOrNull()
            ?: return json

    if (arr.length() == 0) {
        return "(empty)"
    }

    return buildString {
        for (i in 0 until arr.length()) {
            val element = arr.get(i)
            if (element is JSONObject) {
                appendLine("${i + 1}. ${summarizeObject(element)}")
            } else {
                appendLine("${i + 1}. $element")
            }
        }
    }.trimEnd()
}

/**
 * Summarises a JSON object as a single line of key-value pairs.
 *
 * @param obj The JSON object to summarise.
 * @return A compact "key=value, key=value" representation.
 */
private fun summarizeObject(obj: JSONObject): String {
    val keys = obj.keys().asSequence().toList()
    return keys.joinToString(", ") { key -> "$key=${obj.get(key)}" }
}

/**
 * Formats a generic JSON object as pretty-printed key-value lines.
 *
 * @param obj The parsed JSON object.
 * @return Multi-line "key: value" text.
 */
private fun formatGenericObject(obj: JSONObject): String = runCatching { obj.toString(JSON_INDENT_SPACES) }.getOrDefault(obj.toString())
