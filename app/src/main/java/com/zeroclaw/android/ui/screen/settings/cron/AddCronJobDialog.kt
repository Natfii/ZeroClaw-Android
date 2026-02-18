// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings.cron

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Index for the "Recurring" mode tab. */
private const val MODE_RECURRING = 0

/** Index for the "One-shot" mode tab. */
private const val MODE_ONE_SHOT = 1

/** Minimum number of cron expression parts (standard cron has 5). */
private const val MIN_CRON_PARTS = 5

/** Maximum number of cron expression parts (extended cron has 6 with seconds). */
private const val MAX_CRON_PARTS = 6

/**
 * Dialog for adding a new cron job.
 *
 * Provides two modes: "Recurring" for cron-expression-based jobs and
 * "One-shot" for delay-based jobs that fire once.
 *
 * @param onAddRecurring Called when the user submits a recurring job.
 *   Receives the cron expression and command.
 * @param onAddOneShot Called when the user submits a one-shot job.
 *   Receives the delay string and command.
 * @param onDismiss Called when the dialog is dismissed.
 */
@Composable
fun AddCronJobDialog(
    onAddRecurring: (expression: String, command: String) -> Unit,
    onAddOneShot: (delay: String, command: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMode by remember { mutableIntStateOf(MODE_RECURRING) }
    var expression by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var expressionError by remember { mutableStateOf<String?>(null) }
    var delayError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scheduled Job") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Job type selection"
                            },
                ) {
                    SegmentedButton(
                        selected = selectedMode == MODE_RECURRING,
                        onClick = { selectedMode = MODE_RECURRING },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = MODE_RECURRING,
                                count = MODE_ONE_SHOT + 1,
                            ),
                    ) {
                        Text("Recurring")
                    }
                    SegmentedButton(
                        selected = selectedMode == MODE_ONE_SHOT,
                        onClick = { selectedMode = MODE_ONE_SHOT },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = MODE_ONE_SHOT,
                                count = MODE_ONE_SHOT + 1,
                            ),
                    ) {
                        Text("One-shot")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedMode == MODE_RECURRING) {
                    OutlinedTextField(
                        value = expression,
                        onValueChange = {
                            expression = it
                            expressionError = null
                        },
                        label = { Text("Cron Expression") },
                        placeholder = { Text("0 */5 * * *") },
                        isError = expressionError != null,
                        supportingText =
                            expressionError?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = delay,
                        onValueChange = {
                            delay = it
                            delayError = null
                        },
                        label = { Text("Delay") },
                        placeholder = { Text("5m, 2h, 30s") },
                        isError = delayError != null,
                        supportingText =
                            delayError?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    placeholder = { Text("Describe the task to execute") },
                    maxLines = MAX_COMMAND_LINES,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedMode == MODE_RECURRING) {
                        val validationError = validateCronExpression(expression)
                        if (validationError != null) {
                            expressionError = validationError
                        } else {
                            onAddRecurring(expression.trim(), command.trim())
                            onDismiss()
                        }
                    } else {
                        val validationError = validateDelay(delay)
                        if (validationError != null) {
                            delayError = validationError
                        } else {
                            onAddOneShot(delay.trim(), command.trim())
                            onDismiss()
                        }
                    }
                },
                enabled = command.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Maximum number of visible lines for the command text field. */
private const val MAX_COMMAND_LINES = 3

/**
 * Validates a cron expression for basic structural correctness.
 *
 * Checks that the expression has 5 or 6 space-separated parts and each part
 * contains only valid cron characters. This is a client-side pre-check;
 * the daemon performs full validation.
 *
 * @param expression The cron expression string to validate.
 * @return An error message if invalid, or null if the expression passes basic validation.
 */
internal fun validateCronExpression(expression: String): String? {
    val trimmed = expression.trim()
    if (trimmed.isBlank()) return "Expression is required"
    val parts = trimmed.split("\\s+".toRegex())
    if (parts.size !in MIN_CRON_PARTS..MAX_CRON_PARTS) {
        return "Expected 5 or 6 space-separated fields"
    }
    val validChars = Regex("^[0-9*,/\\-?LW#]+$")
    for (part in parts) {
        if (!validChars.matches(part)) {
            return "Invalid characters in field: $part"
        }
    }
    return null
}

/**
 * Validates a delay string for basic format correctness.
 *
 * Checks that the delay is non-blank and ends with a recognised time unit
 * suffix (s, m, h, d). The daemon performs full parsing.
 *
 * @param delay The delay string to validate.
 * @return An error message if invalid, or null if the delay passes basic validation.
 */
internal fun validateDelay(delay: String): String? {
    val trimmed = delay.trim()
    if (trimmed.isBlank()) return "Delay is required"
    if (!trimmed.matches(Regex("^\\d+[smhd]$"))) {
        return "Expected format: number + unit (s, m, h, d)"
    }
    return null
}
