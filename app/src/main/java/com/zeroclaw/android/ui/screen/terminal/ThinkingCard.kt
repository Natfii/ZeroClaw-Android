/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.TerminalTypography
import com.zeroclaw.android.util.LocalPowerSaveMode

/** Maximum height for the thinking card in dp. */
private const val MAX_CARD_HEIGHT_DP = 200

/** Padding inside the thinking card in dp. */
private const val CARD_PADDING_DP = 12

/** Spacing between header row and body text in dp. */
private const val BODY_SPACING_DP = 8

/**
 * Card displaying live thinking/reasoning tokens from the model.
 *
 * Shows a [BrailleSpinner] with a "Thinking..." label in the header row,
 * accumulated thinking tokens in a scrolling body, and a "Cancel" button
 * in the footer. The card auto-scrolls to the bottom as new tokens arrive.
 *
 * When tool activity is present, a divider and tool progress footer are
 * appended below the thinking text, showing in-flight and completed tools.
 *
 * When [LocalPowerSaveMode] is active, entry/exit animations are disabled
 * to conserve battery.
 *
 * @param thinkingText Accumulated thinking tokens to display.
 * @param visible Whether the card is currently visible.
 * @param onCancel Callback when the user taps the cancel button.
 * @param activeTools Tools currently executing during the turn.
 * @param toolResults Completed tool execution results for the current turn.
 * @param progressMessage Miscellaneous progress status (e.g. "Searching memory...").
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun ThinkingCard(
    thinkingText: String,
    visible: Boolean,
    onCancel: () -> Unit,
    activeTools: List<ToolProgress> = emptyList(),
    toolResults: List<ToolResultEntry> = emptyList(),
    progressMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val isPowerSave = LocalPowerSaveMode.current
    val enterTransition =
        if (isPowerSave) EnterTransition.None else expandVertically()
    val exitTransition =
        if (isPowerSave) ExitTransition.None else shrinkVertically()

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier,
    ) {
        ElevatedCard(
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Model is thinking"
                        liveRegion = LiveRegionMode.Polite
                    },
        ) {
            Column(
                modifier = Modifier.padding(CARD_PADDING_DP.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrailleSpinner(label = "Thinking\u2026")
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onCancel,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Cancel request"
                            },
                    ) {
                        Text(
                            text = "Cancel",
                            style = TerminalTypography.labelMedium,
                        )
                    }
                }

                if (thinkingText.isNotEmpty()) {
                    val scrollState = rememberScrollState()

                    LaunchedEffect(thinkingText) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    Text(
                        text = thinkingText,
                        style = TerminalTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = MAX_CARD_HEIGHT_DP.dp)
                                .verticalScroll(scrollState)
                                .padding(top = BODY_SPACING_DP.dp),
                    )
                }

                ToolActivityFooter(
                    activeTools = activeTools,
                    toolResults = toolResults,
                    progressMessage = progressMessage,
                )
            }
        }
    }
}

/**
 * Tool activity footer displaying in-flight and completed tool executions.
 *
 * Renders a [HorizontalDivider] followed by optional progress text,
 * active tool rows (hourglass indicator), and completed tool rows
 * (check/cross indicator with duration).
 *
 * @param activeTools Tools currently executing during the turn.
 * @param toolResults Completed tool execution results for the current turn.
 * @param progressMessage Miscellaneous progress status text.
 */
@Composable
private fun ToolActivityFooter(
    activeTools: List<ToolProgress>,
    toolResults: List<ToolResultEntry>,
    progressMessage: String?,
) {
    if (activeTools.isEmpty() && toolResults.isEmpty() && progressMessage == null) {
        return
    }

    HorizontalDivider(
        modifier = Modifier.padding(vertical = BODY_SPACING_DP.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )

    if (progressMessage != null) {
        Text(
            text = progressMessage,
            style = TerminalTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    for (tool in activeTools) {
        ToolActivityRow(
            icon = "\u23F3",
            name = tool.name,
            detail = tool.hint,
            tint = MaterialTheme.colorScheme.tertiary,
        )
    }

    for (result in toolResults) {
        val icon = if (result.success) "\u2705" else "\u274C"
        val detail =
            if (result.durationSecs > 0) {
                "(${result.durationSecs}s)"
            } else {
                ""
            }
        ToolActivityRow(
            icon = icon,
            name = result.name,
            detail = detail,
            tint =
                if (result.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}

/**
 * Single row in the tool activity footer showing tool name with status indicator.
 *
 * @param icon Emoji indicator for the tool state.
 * @param name Tool identifier.
 * @param detail Additional detail text (hint or duration).
 * @param tint Color for the tool name text.
 */
@Composable
private fun ToolActivityRow(
    icon: String,
    name: String,
    detail: String,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$name tool $detail"
                },
    ) {
        Text(
            text = icon,
            style = TerminalTypography.bodySmall,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = name,
            style = TerminalTypography.bodySmall,
            color = tint,
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = TerminalTypography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
