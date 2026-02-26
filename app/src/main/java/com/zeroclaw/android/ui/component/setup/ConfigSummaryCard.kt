/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
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
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Internal padding for the summary card. */
private val CardPadding = 16.dp

/** Spacing after the title text. */
private val TitleSpacing = 16.dp

/** Vertical spacing between summary rows. */
private val RowSpacing = 12.dp

/** Size of the status icon in each summary row. */
private val StatusIconSize = 20.dp

/** Spacing between the status icon and the label text. */
private val IconTextSpacing = 8.dp

/**
 * Aggregated configuration values displayed in the summary card.
 *
 * This data class mirrors the key fields from
 * [GlobalTomlConfig][com.zeroclaw.android.service.GlobalTomlConfig] and
 * agent-level settings to present a human-readable overview of the current
 * setup state.
 *
 * @property provider Selected provider ID (e.g. "openai"), or blank if unconfigured.
 * @property model Selected model name (e.g. "gpt-4o"), or blank if unconfigured.
 * @property autonomy Autonomy level: "supervised", "constrained", or "unconstrained".
 * @property memoryBackend Memory backend: "sqlite", "markdown", or "none".
 * @property autoSave Whether the memory auto-save feature is enabled.
 * @property channels List of configured channel display names.
 * @property tunnel Tunnel provider: "none", "ngrok", "cloudflare", or "custom".
 * @property identityFormat Identity format: "openclaw" or "aieos".
 * @property agentName The configured agent name, or blank if not set.
 */
data class ConfigSummary(
    val provider: String = "",
    val model: String = "",
    val autonomy: String = "supervised",
    val memoryBackend: String = "sqlite",
    val autoSave: Boolean = true,
    val channels: List<String> = emptyList(),
    val tunnel: String = "none",
    val identityFormat: String = "aieos",
    val agentName: String = "",
)

/**
 * Configuration summary card displaying a read-only overview of all setup values.
 *
 * Renders an [ElevatedCard] containing labelled rows for each configuration
 * dimension. Each row includes a status icon: a green check-circle for
 * configured values and an amber warning triangle for missing or default values
 * that may need attention.
 *
 * The layout matches the upstream CLI `print_summary` output for consistency
 * across platforms.
 *
 * @param summary The aggregated configuration values to display.
 * @param modifier Modifier applied to the [ElevatedCard].
 */
@Composable
fun ConfigSummaryCard(
    summary: ConfigSummary,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
        ) {
            Text(
                text = "Configuration Summary",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(TitleSpacing))

            SummaryRow(
                label = "Provider",
                value = summary.provider.ifBlank { "Not configured" },
                isConfigured = summary.provider.isNotBlank(),
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            SummaryRow(
                label = "Model",
                value = summary.model.ifBlank { "Not configured" },
                isConfigured = summary.model.isNotBlank(),
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            SummaryRow(
                label = "Autonomy",
                value = summary.autonomy.replaceFirstChar { it.uppercase() },
                isConfigured = true,
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            val memoryValue =
                buildString {
                    append(summary.memoryBackend.replaceFirstChar { it.uppercase() })
                    if (summary.autoSave) {
                        append(" (auto-save)")
                    }
                }
            SummaryRow(
                label = "Memory",
                value = memoryValue,
                isConfigured = true,
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            val channelsValue =
                if (summary.channels.isEmpty()) {
                    "None configured"
                } else {
                    summary.channels.joinToString(", ")
                }
            SummaryRow(
                label = "Channels",
                value = channelsValue,
                isConfigured = summary.channels.isNotEmpty(),
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            SummaryRow(
                label = "Tunnel",
                value = summary.tunnel.replaceFirstChar { it.uppercase() },
                isConfigured = true,
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            SummaryRow(
                label = "Identity",
                value =
                    when (summary.identityFormat) {
                        "aieos" -> "AIEOS"
                        else -> "OpenClaw"
                    },
                isConfigured = true,
            )

            Spacer(modifier = Modifier.height(RowSpacing))

            SummaryRow(
                label = "Agent",
                value = summary.agentName.ifBlank { "Not named" },
                isConfigured = summary.agentName.isNotBlank(),
            )
        }
    }
}

/**
 * A single label-value row with a leading status icon.
 *
 * Configured values display a green check-circle; unconfigured or warning
 * values display an amber warning icon. Both colour and icon shape are used
 * so that colour is never the sole differentiator (WCAG 2.2 AA).
 *
 * @param label The configuration dimension name.
 * @param value The current value to display.
 * @param isConfigured Whether the value is considered properly configured.
 */
@Composable
private fun SummaryRow(
    label: String,
    value: String,
    isConfigured: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label: $value"
                },
    ) {
        Icon(
            imageVector =
                if (isConfigured) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.Warning
                },
            contentDescription = null,
            tint =
                if (isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            modifier = Modifier.size(StatusIconSize),
        )
        Spacer(modifier = Modifier.width(IconTextSpacing))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isConfigured) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "Summary - Fully Configured")
@Composable
private fun PreviewFullyConfigured() {
    ZeroClawTheme {
        Surface {
            ConfigSummaryCard(
                summary =
                    ConfigSummary(
                        provider = "openai",
                        model = "gpt-4o",
                        autonomy = "supervised",
                        memoryBackend = "sqlite",
                        autoSave = true,
                        channels = listOf("Telegram", "Discord"),
                        tunnel = "ngrok",
                        identityFormat = "openclaw",
                        agentName = "ZeroClaw",
                    ),
            )
        }
    }
}

@Preview(name = "Summary - Minimal")
@Composable
private fun PreviewMinimal() {
    ZeroClawTheme {
        Surface {
            ConfigSummaryCard(
                summary =
                    ConfigSummary(
                        provider = "",
                        model = "",
                        autonomy = "supervised",
                        memoryBackend = "sqlite",
                        autoSave = true,
                        channels = emptyList(),
                        tunnel = "none",
                        identityFormat = "openclaw",
                        agentName = "",
                    ),
            )
        }
    }
}

@Preview(name = "Summary - AIEOS Unconstrained")
@Composable
private fun PreviewAieos() {
    ZeroClawTheme {
        Surface {
            ConfigSummaryCard(
                summary =
                    ConfigSummary(
                        provider = "anthropic",
                        model = "claude-sonnet-4-20250514",
                        autonomy = "unconstrained",
                        memoryBackend = "markdown",
                        autoSave = false,
                        channels = listOf("Slack"),
                        tunnel = "cloudflare",
                        identityFormat = "aieos",
                        agentName = "Atlas",
                    ),
            )
        }
    }
}

@Preview(
    name = "Summary - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            ConfigSummaryCard(
                summary =
                    ConfigSummary(
                        provider = "openai",
                        model = "gpt-4o",
                        autonomy = "constrained",
                        memoryBackend = "sqlite",
                        autoSave = true,
                        channels = listOf("Telegram"),
                        tunnel = "none",
                        identityFormat = "openclaw",
                        agentName = "ZeroClaw",
                    ),
            )
        }
    }
}
