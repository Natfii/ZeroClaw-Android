/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Icon size used for all validation indicator icons. */
private val IconSize = 20.dp

/** Spacing between the icon and the text label. */
private val IconTextSpacing = 8.dp

/** Size of the indeterminate progress spinner shown during validation. */
private val SpinnerSize = 16.dp

/** Stroke width of the indeterminate progress spinner. */
private val SpinnerStroke = 2.dp

/**
 * Renders a [ValidationResult] inline with an appropriate icon and text.
 *
 * Each terminal state is styled distinctly:
 * - [ValidationResult.Idle] renders nothing (empty [Box]).
 * - [ValidationResult.Loading] shows a small spinner with "Validating..." text.
 * - [ValidationResult.Success] shows a check-circle icon and details text in primary colour.
 * - [ValidationResult.Failure] shows an error icon and message text in error colour.
 * - [ValidationResult.Offline] shows a cloud-off icon and message text in tertiary colour.
 *
 * Status indicators use both colour and icon shape so that colour is never the sole
 * differentiator (WCAG 2.2 AA).
 *
 * @param result The current validation state to display.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ValidationIndicator(
    result: ValidationResult,
    modifier: Modifier = Modifier,
) {
    when (result) {
        is ValidationResult.Idle -> {
            Box(modifier = modifier)
        }
        is ValidationResult.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Validating"
                    },
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SpinnerSize),
                    strokeWidth = SpinnerStroke,
                )
                Spacer(modifier = Modifier.width(IconTextSpacing))
                Text(
                    text = "Validating\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is ValidationResult.Success -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Validation succeeded: ${result.details}"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(IconTextSpacing))
                Text(
                    text = result.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        is ValidationResult.Failure -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Validation failed: ${result.message}"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(IconTextSpacing))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        is ValidationResult.Offline -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Offline: ${result.message}"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(IconTextSpacing))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Preview(name = "Idle")
@Composable
private fun PreviewIdle() {
    ZeroClawTheme { Surface { ValidationIndicator(result = ValidationResult.Idle) } }
}

@Preview(name = "Loading")
@Composable
private fun PreviewLoading() {
    ZeroClawTheme { Surface { ValidationIndicator(result = ValidationResult.Loading) } }
}

@Preview(name = "Success")
@Composable
private fun PreviewSuccess() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Success(details = "3 models available"),
            )
        }
    }
}

@Preview(name = "Failure")
@Composable
private fun PreviewFailure() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Failure(message = "Invalid API key"),
            )
        }
    }
}

@Preview(name = "Offline")
@Composable
private fun PreviewOffline() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Offline(message = "No internet connection"),
            )
        }
    }
}

@Preview(name = "Idle - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewIdleDark() {
    ZeroClawTheme { Surface { ValidationIndicator(result = ValidationResult.Idle) } }
}

@Preview(name = "Loading - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLoadingDark() {
    ZeroClawTheme { Surface { ValidationIndicator(result = ValidationResult.Loading) } }
}

@Preview(name = "Success - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSuccessDark() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Success(details = "3 models available"),
            )
        }
    }
}

@Preview(name = "Failure - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFailureDark() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Failure(message = "Invalid API key"),
            )
        }
    }
}

@Preview(name = "Offline - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewOfflineDark() {
    ZeroClawTheme {
        Surface {
            ValidationIndicator(
                result = ValidationResult.Offline(message = "No internet connection"),
            )
        }
    }
}
