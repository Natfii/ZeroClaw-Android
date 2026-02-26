/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import com.zeroclaw.android.ui.component.setup.ConfigSummary
import com.zeroclaw.android.ui.component.setup.ConfigSummaryCard
import com.zeroclaw.android.ui.theme.ZeroClawTheme
import com.zeroclaw.android.util.LocalPowerSaveMode

/** Spacing after the title text. */
private val TitleSpacing = 16.dp

/** Spacing after the description text. */
private val DescriptionSpacing = 24.dp

/** Spacing after the config summary card. */
private val CardSpacing = 24.dp

/** Minimum height for the activation button (WCAG AA touch target). */
private val MinButtonHeight = 48.dp

/** Stroke width for the progress indicator inside the button. */
private val ProgressStrokeWidth = 2.dp

/** Height of the progress indicator inside the button. */
private val ProgressHeight = 24.dp

/**
 * Final onboarding step displaying a configuration summary and activation button.
 *
 * Renders a [ConfigSummaryCard] with the aggregated setup values followed by
 * a button to start the daemon and complete onboarding.
 *
 * @param configSummary Aggregated configuration values to display.
 * @param onActivate Callback invoked when the user starts the daemon
 *   and finishes onboarding.
 * @param isActivating Whether the activation is in progress (probing
 *   credentials and persisting configuration). When true, the button is
 *   disabled and a progress indicator is shown.
 * @param modifier Modifier applied to the root [Column].
 */
@Composable
fun ActivationStep(
    configSummary: ConfigSummary = ConfigSummary(),
    onActivate: () -> Unit,
    isActivating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Ready to Go",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(TitleSpacing))
        Text(
            text = "Review your configuration and start the ZeroClaw daemon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DescriptionSpacing))

        ConfigSummaryCard(
            summary = configSummary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(CardSpacing))

        FilledTonalButton(
            onClick = onActivate,
            enabled = !isActivating,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinButtonHeight)
                    .semantics {
                        contentDescription = "Start daemon and finish setup"
                    },
        ) {
            if (isActivating) {
                if (LocalPowerSaveMode.current) {
                    Text("Verifying\u2026")
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.height(ProgressHeight),
                        strokeWidth = ProgressStrokeWidth,
                    )
                }
            } else {
                Text("Start Daemon")
            }
        }
    }
}

@Preview(name = "Activation - Ready")
@Composable
private fun PreviewReady() {
    ZeroClawTheme {
        Surface {
            ActivationStep(
                configSummary =
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
                onActivate = {},
            )
        }
    }
}

@Preview(name = "Activation - Activating")
@Composable
private fun PreviewActivating() {
    ZeroClawTheme {
        Surface {
            ActivationStep(
                configSummary =
                    ConfigSummary(
                        provider = "anthropic",
                        model = "claude-sonnet-4-20250514",
                    ),
                onActivate = {},
                isActivating = true,
            )
        }
    }
}

@Preview(name = "Activation - Minimal")
@Composable
private fun PreviewMinimal() {
    ZeroClawTheme {
        Surface {
            ActivationStep(
                onActivate = {},
            )
        }
    }
}

@Preview(
    name = "Activation - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            ActivationStep(
                configSummary =
                    ConfigSummary(
                        provider = "openai",
                        model = "gpt-4o",
                        agentName = "ZeroClaw",
                    ),
                onActivate = {},
            )
        }
    }
}
