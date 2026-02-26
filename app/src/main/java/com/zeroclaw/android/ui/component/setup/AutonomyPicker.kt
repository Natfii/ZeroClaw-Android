/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Spacing after the title text. */
private val TitleSpacing = 8.dp

/** Spacing between the description and the card group. */
private val DescriptionSpacing = 16.dp

/** Vertical spacing between autonomy option cards. */
private val CardSpacing = 8.dp

/** Internal padding for each autonomy option card. */
private val CardPadding = 16.dp

/** Border width for the selected autonomy card. */
private val SelectedBorderWidth = 2.dp

/** Size of the leading icon in each autonomy card. */
private val IconSize = 24.dp

/** Spacing between the icon and the text content. */
private val IconTextSpacing = 12.dp

/**
 * Describes an autonomy level option for display in the picker.
 *
 * @property id Machine-readable identifier matching upstream TOML `autonomy.level`.
 * @property title Human-readable option name.
 * @property description Brief explanation of the autonomy behaviour.
 * @property icon Material icon representing this autonomy level.
 * @property isWarning Whether the option should use warning styling.
 */
private data class AutonomyOption(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isWarning: Boolean = false,
)

/** Available autonomy levels presented to the user. */
private val AUTONOMY_OPTIONS =
    listOf(
        AutonomyOption(
            id = "supervised",
            title = "Supervised",
            description = "Agent asks before taking actions. Recommended for most users.",
            icon = Icons.Filled.Shield,
        ),
        AutonomyOption(
            id = "constrained",
            title = "Constrained",
            description = "Agent acts within defined boundaries without asking.",
            icon = Icons.Filled.Security,
        ),
        AutonomyOption(
            id = "unconstrained",
            title = "Unconstrained",
            description = "Agent acts freely with no restrictions.",
            icon = Icons.Filled.Warning,
            isWarning = true,
        ),
    )

/**
 * Autonomy level selector for configuring agent independence.
 *
 * Presents three selectable [Card] options, each with a descriptive
 * icon, title, and description. The "Unconstrained" option uses warning styling
 * (amber border and error-container background when selected) to highlight the
 * risk involved.
 *
 * The selected level value aligns with
 * [GlobalTomlConfig.autonomyLevel][com.zeroclaw.android.service.GlobalTomlConfig.autonomyLevel]:
 * "supervised", "constrained", or "unconstrained".
 *
 * @param selectedLevel Current autonomy level identifier.
 * @param onLevelChanged Callback when the user selects a different autonomy level.
 * @param modifier Modifier applied to the root scrollable [Column].
 */
@Composable
fun AutonomyPicker(
    selectedLevel: String,
    onLevelChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Security & Autonomy",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(TitleSpacing))

        Text(
            text = "Control how independently your agent can act.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(DescriptionSpacing))

        AUTONOMY_OPTIONS.forEach { option ->
            val isSelected = selectedLevel == option.id

            AutonomyOptionCard(
                option = option,
                isSelected = isSelected,
                onClick = { onLevelChanged(option.id) },
            )

            Spacer(modifier = Modifier.height(CardSpacing))
        }
    }
}

/**
 * A single selectable autonomy option card with leading icon.
 *
 * Warning-level options use distinct styling when selected: the error colour
 * scheme replaces the primary colour scheme to signal elevated risk. All cards
 * meet the 48x48dp minimum touch target through [Card] defaults.
 *
 * @param option The autonomy option to display.
 * @param isSelected Whether this option is currently selected.
 * @param onClick Callback invoked when the card is tapped.
 */
@Composable
private fun AutonomyOptionCard(
    option: AutonomyOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        when {
            isSelected && option.isWarning -> MaterialTheme.colorScheme.error
            isSelected -> MaterialTheme.colorScheme.primary
            else -> null
        }
    val containerColor =
        when {
            isSelected && option.isWarning ->
                MaterialTheme.colorScheme.errorContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val iconTint =
        when {
            option.isWarning -> MaterialTheme.colorScheme.error
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
        border =
            borderColor?.let {
                BorderStroke(SelectedBorderWidth, it)
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${option.title}, ${if (isSelected) "selected" else "not selected"}"
                    role = Role.RadioButton
                    selected = isSelected
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(CardPadding),
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(IconSize),
            )
            Spacer(modifier = Modifier.width(IconTextSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Autonomy - Supervised")
@Composable
private fun PreviewSupervised() {
    ZeroClawTheme {
        Surface {
            AutonomyPicker(
                selectedLevel = "supervised",
                onLevelChanged = {},
            )
        }
    }
}

@Preview(name = "Autonomy - Constrained")
@Composable
private fun PreviewConstrained() {
    ZeroClawTheme {
        Surface {
            AutonomyPicker(
                selectedLevel = "constrained",
                onLevelChanged = {},
            )
        }
    }
}

@Preview(name = "Autonomy - Unconstrained")
@Composable
private fun PreviewUnconstrained() {
    ZeroClawTheme {
        Surface {
            AutonomyPicker(
                selectedLevel = "unconstrained",
                onLevelChanged = {},
            )
        }
    }
}

@Preview(
    name = "Autonomy - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            AutonomyPicker(
                selectedLevel = "supervised",
                onLevelChanged = {},
            )
        }
    }
}
