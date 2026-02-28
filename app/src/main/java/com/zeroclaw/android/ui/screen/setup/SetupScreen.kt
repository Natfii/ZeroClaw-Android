/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.util.LocalPowerSaveMode

/** Icon size for step status indicators. */
private val StepIconSize = 24.dp

/** Spacing between the status icon and the step label. */
private val IconLabelSpacing = 12.dp

/** Horizontal edge margin for compact layout. */
private val EdgeMargin = 16.dp

/** Vertical padding on step rows to meet 48dp touch target minimum. */
private val StepRowVerticalPadding = 8.dp

/** Stroke width for the running step progress indicator. */
private val ProgressStrokeWidth = 2.dp

/** Minimum touch target height for buttons (WCAG AA). */
private val MinButtonHeight = 48.dp

/**
 * Setup screen showing step-by-step progress as the daemon starts
 * and channels come online.
 *
 * Observes [SetupViewModel.progress] for real-time step feedback and
 * triggers the setup pipeline on first composition via [LaunchedEffect].
 * The bottom bar shows a Skip button while setup is in progress and a
 * Done button once all steps have resolved.
 *
 * @param onComplete Callback invoked when the user taps Skip or Done
 *   to leave the setup screen.
 * @param viewModel The [SetupViewModel] driving the setup pipeline.
 * @param modifier Modifier applied to the root [Scaffold].
 */
@Composable
fun SetupScreen(
    onComplete: () -> Unit,
    viewModel: SetupViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startSetup()
    }

    SetupContent(
        progress = progress,
        onComplete = onComplete,
        modifier = modifier,
    )
}

/**
 * Stateless setup content composable for testing.
 *
 * Renders the header, core setup steps, optional channel section,
 * and a bottom bar with Skip/Done buttons. All state is passed as
 * parameters with no ViewModel dependency.
 *
 * @param progress Current aggregate progress across all setup steps.
 * @param onComplete Callback invoked when the user taps Skip or Done.
 * @param modifier Modifier applied to the root [Scaffold].
 */
@Composable
internal fun SetupContent(
    progress: SetupProgress,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val powerSave = LocalPowerSaveMode.current

    Scaffold(
        modifier = modifier,
        bottomBar = {
            SetupBottomBar(
                isComplete = progress.isComplete,
                onComplete = onComplete,
                powerSave = powerSave,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = EdgeMargin)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Setting up ZeroClaw",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Please don't close the app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SetupStepRow(
                label = "Validating configuration",
                status = progress.configValidation,
                powerSave = powerSave,
            )
            SetupStepRow(
                label = "Creating workspace",
                status = progress.workspaceScaffold,
                powerSave = powerSave,
            )
            SetupStepRow(
                label = "Starting daemon",
                status = progress.daemonStart,
                powerSave = powerSave,
            )
            SetupStepRow(
                label = "Checking daemon health",
                status = progress.daemonHealth,
                powerSave = powerSave,
            )

            if (progress.channels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Channels",
                    style = MaterialTheme.typography.titleSmall,
                )

                progress.channels.forEach { (name, status) ->
                    SetupStepRow(
                        label = name.replaceFirstChar { it.uppercase() },
                        status = status,
                        powerSave = powerSave,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A single row displaying a setup step's label and status icon.
 *
 * The icon changes based on [status]: a clock for pending, a spinning
 * progress indicator for running (static text in power-save mode),
 * a green check for success, or a red cancel icon with error text
 * for failure.
 *
 * @param label Human-readable name of the step.
 * @param status Current execution status of this step.
 * @param powerSave Whether power-save mode is active, disabling animations.
 * @param modifier Modifier applied to the root [Row].
 */
@Composable
private fun SetupStepRow(
    label: String,
    status: SetupStepStatus,
    powerSave: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusText =
        when (status) {
            SetupStepStatus.Pending -> "pending"
            SetupStepStatus.Running -> "running"
            SetupStepStatus.Success -> "success"
            is SetupStepStatus.Failed -> "failed"
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinButtonHeight)
                .padding(vertical = StepRowVerticalPadding)
                .semantics { contentDescription = "$label: $statusText" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepStatusIcon(status = status, powerSave = powerSave)
        Spacer(modifier = Modifier.width(IconLabelSpacing))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (status is SetupStepStatus.Failed) {
                Text(
                    text = status.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Renders the appropriate icon for a [SetupStepStatus].
 *
 * Uses [Icons.Filled.Schedule] for pending, a [CircularProgressIndicator]
 * for running (or static ellipsis text in power-save mode),
 * [Icons.Filled.CheckCircle] for success, and [Icons.Filled.Cancel]
 * for failed.
 *
 * @param status The step status to render.
 * @param powerSave Whether power-save mode is active, disabling animations.
 */
@Composable
private fun StepStatusIcon(
    status: SetupStepStatus,
    powerSave: Boolean,
) {
    when (status) {
        SetupStepStatus.Pending -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(StepIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SetupStepStatus.Running -> {
            if (powerSave) {
                Text(
                    text = "\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.size(StepIconSize),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(StepIconSize),
                    strokeWidth = ProgressStrokeWidth,
                )
            }
        }

        SetupStepStatus.Success -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(StepIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        is SetupStepStatus.Failed -> {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(StepIconSize),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Bottom bar with Skip and Done buttons for the setup screen.
 *
 * The Skip button (outlined) is visible when setup has not yet completed,
 * allowing the user to bypass the remainder of the setup flow. The Done
 * button (filled) appears once [isComplete] is true. Transitions use
 * [AnimatedVisibility] with fade animations, falling back to instant
 * transitions in power-save mode.
 *
 * @param isComplete Whether the entire setup flow has resolved.
 * @param onComplete Callback invoked when the user taps either button.
 * @param powerSave Whether power-save mode is active, disabling animations.
 */
@Composable
private fun SetupBottomBar(
    isComplete: Boolean,
    onComplete: () -> Unit,
    powerSave: Boolean,
) {
    val enter = if (powerSave) EnterTransition.None else fadeIn()
    val exit = if (powerSave) ExitTransition.None else fadeOut()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = EdgeMargin, vertical = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = !isComplete,
            enter = enter,
            exit = exit,
        ) {
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.defaultMinSize(minHeight = MinButtonHeight),
            ) {
                Text("Skip")
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        AnimatedVisibility(
            visible = isComplete,
            enter = enter,
            exit = exit,
        ) {
            Button(
                onClick = onComplete,
                modifier = Modifier.defaultMinSize(minHeight = MinButtonHeight),
            ) {
                Text("Done")
            }
        }
    }
}
