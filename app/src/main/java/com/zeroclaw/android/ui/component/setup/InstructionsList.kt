/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.channel.InstructionItem
import com.zeroclaw.android.ui.theme.ZeroClawTheme
import com.zeroclaw.android.util.LocalPowerSaveMode

/** Vertical spacing between instruction items. */
private val ItemSpacing = 8.dp

/** Size of the circled number badge for numbered steps. */
private val NumberBadgeSize = 24.dp

/** Size of the warning icon. */
private val WarningIconSize = 20.dp

/** Spacing between a leading element (badge/icon) and the text. */
private val LeadingTextSpacing = 8.dp

/**
 * Renders a list of [InstructionItem] entries as a vertical column.
 *
 * Each item variant has a distinct visual treatment:
 * - [InstructionItem.Text] renders as plain body text.
 * - [InstructionItem.NumberedStep] renders with a circled number badge.
 * - [InstructionItem.Warning] renders with a warning icon in tertiary colour.
 * - [InstructionItem.Hint] renders as subdued body-small text; when
 *   [InstructionItem.Hint.expandable] is true, the text is wrapped in a
 *   collapsible "Show hint" / "Hide hint" toggle.
 *
 * @param items Ordered instruction items to display.
 * @param modifier Modifier applied to the root [Column].
 */
@Composable
fun InstructionsList(
    items: List<InstructionItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        modifier = modifier,
    ) {
        items.forEach { item ->
            when (item) {
                is InstructionItem.Text -> InstructionText(content = item.content)
                is InstructionItem.NumberedStep ->
                    InstructionNumberedStep(number = item.number, content = item.content)
                is InstructionItem.Warning -> InstructionWarning(content = item.content)
                is InstructionItem.Hint ->
                    InstructionHint(content = item.content, expandable = item.expandable)
            }
        }
    }
}

/**
 * Plain descriptive text instruction.
 *
 * @param content The text to display.
 */
@Composable
private fun InstructionText(content: String) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Numbered step with a circled number badge in primary-container colour.
 *
 * @param number The step number to display inside the badge.
 * @param content The instruction text for this step.
 */
@Composable
private fun InstructionNumberedStep(
    number: Int,
    content: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Step $number: $content"
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(NumberBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.width(LeadingTextSpacing))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Warning callout with a warning icon in tertiary colour.
 *
 * @param content The warning message to display.
 */
@Composable
private fun InstructionWarning(content: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Warning: $content"
            },
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(WarningIconSize),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.width(LeadingTextSpacing))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * Hint text, optionally wrapped in a collapsible toggle.
 *
 * When [expandable] is true, the hint starts collapsed behind a "Show hint"
 * label and can be toggled by tapping. When false, the text is rendered
 * directly in subdued styling.
 *
 * Animations are suppressed when battery saver is active.
 *
 * @param content The hint text to display.
 * @param expandable Whether the hint should be collapsible.
 */
@Composable
private fun InstructionHint(
    content: String,
    expandable: Boolean,
) {
    if (expandable) {
        ExpandableHint(content = content)
    } else {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Collapsible hint with a "Show hint" / "Hide hint" toggle.
 *
 * Expand and collapse animations respect the device's battery saver state.
 *
 * @param content The hint text revealed when expanded.
 */
@Composable
private fun ExpandableHint(content: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isPowerSave = LocalPowerSaveMode.current
    val toggleLabel = if (expanded) "Hide hint" else "Show hint"

    Column {
        Text(
            text = toggleLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription =
                            if (expanded) "Hide hint, currently showing" else "Show hint"
                    },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = if (isPowerSave) EnterTransition.None else expandVertically(),
            exit = if (isPowerSave) ExitTransition.None else shrinkVertically(),
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Instructions List")
@Composable
private fun PreviewInstructionsList() {
    ZeroClawTheme {
        Surface {
            InstructionsList(
                items =
                    listOf(
                        InstructionItem.Text("Create a new bot using Telegram's BotFather."),
                        InstructionItem.NumberedStep(number = 1, content = "Open BotFather using the link below."),
                        InstructionItem.NumberedStep(number = 2, content = "Send /newbot and follow the prompts."),
                        InstructionItem.NumberedStep(number = 3, content = "Copy the API token BotFather gives you."),
                        InstructionItem.Warning(content = "Keep your bot token secret."),
                        InstructionItem.Hint(content = "You can add multiple IDs separated by commas.", expandable = true),
                        InstructionItem.Hint(content = "This is a non-expandable hint.", expandable = false),
                    ),
            )
        }
    }
}

@Preview(
    name = "Instructions List - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewInstructionsListDark() {
    ZeroClawTheme {
        Surface {
            InstructionsList(
                items =
                    listOf(
                        InstructionItem.Text(content = "Create a new bot using Telegram's BotFather."),
                        InstructionItem.NumberedStep(number = 1, content = "Open BotFather using the link below."),
                        InstructionItem.NumberedStep(number = 2, content = "Send /newbot and follow the prompts."),
                        InstructionItem.NumberedStep(number = 3, content = "Copy the API token BotFather gives you."),
                        InstructionItem.Warning(content = "Keep your bot token secret."),
                        InstructionItem.Hint(content = "You can add multiple IDs separated by commas.", expandable = true),
                        InstructionItem.Hint(content = "This is a non-expandable hint.", expandable = false),
                    ),
            )
        }
    }
}
