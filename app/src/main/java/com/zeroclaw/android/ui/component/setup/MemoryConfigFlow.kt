/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Spacing between the description and the backend cards. */
private val DescriptionSpacing = 16.dp

/** Vertical spacing between backend option cards. */
private val CardSpacing = 8.dp

/** Internal padding for each backend option card. */
private val CardPadding = 16.dp

/** Border width for the selected backend card. */
private val SelectedBorderWidth = 2.dp

/** Spacing between major form sections. */
private val SectionSpacing = 24.dp

/** Spacing between the section label and its control. */
private val LabelSpacing = 8.dp

/** Spacing between retention chips. */
private val ChipSpacing = 8.dp

/** Retention period representing "forever" (no expiration). */
private const val RETENTION_FOREVER = -1

/**
 * Describes a memory backend option for display in the selector.
 *
 * @property id Machine-readable identifier matching upstream TOML `memory.backend`.
 * @property title Human-readable option name.
 * @property description Brief explanation of the backend behaviour.
 */
private data class BackendOption(
    val id: String,
    val title: String,
    val description: String,
)

/** Available memory backend options. */
private val BACKEND_OPTIONS =
    listOf(
        BackendOption(
            id = "sqlite",
            title = "SQLite",
            description = "Fast local database. Best for most users.",
        ),
        BackendOption(
            id = "markdown",
            title = "Markdown",
            description = "Plain text files. Easy to read and edit manually.",
        ),
        BackendOption(
            id = "none",
            title = "None",
            description = "No persistent memory. Agent starts fresh each session.",
        ),
    )

/**
 * Describes an embedding provider option for the dropdown menu.
 *
 * @property id Machine-readable identifier matching upstream TOML `memory.embedding_provider`.
 * @property displayName Human-readable name shown in the dropdown.
 */
private data class EmbeddingOption(
    val id: String,
    val displayName: String,
)

/** Available embedding provider options. */
private val EMBEDDING_OPTIONS =
    listOf(
        EmbeddingOption(id = "", displayName = "None"),
        EmbeddingOption(id = "openai", displayName = "OpenAI"),
        EmbeddingOption(id = "anthropic", displayName = "Anthropic"),
    )

/**
 * Describes a retention period option for the chip selector.
 *
 * @property days Number of days, or [RETENTION_FOREVER] for no expiration.
 * @property label Human-readable chip label.
 */
private data class RetentionOption(
    val days: Int,
    val label: String,
)

/** Available retention period options. */
private val RETENTION_OPTIONS =
    listOf(
        RetentionOption(days = 7, label = "7 days"),
        RetentionOption(days = 30, label = "30 days"),
        RetentionOption(days = 90, label = "90 days"),
        RetentionOption(days = RETENTION_FOREVER, label = "Forever"),
    )

/**
 * Memory configuration form for selecting backend, auto-save, embedding, and retention.
 *
 * Presents:
 * 1. Backend selector with three selectable [Card] options.
 * 2. Auto-save toggle as a [Switch] in a labelled [Row].
 * 3. Embedding provider dropdown using [ExposedDropdownMenuBox].
 * 4. Retention period picker using [FilterChip] options.
 *
 * Field names align with [GlobalTomlConfig][com.zeroclaw.android.service.GlobalTomlConfig]:
 * `memoryBackend`, `memoryAutoSave`, `memoryEmbeddingProvider`, and `memoryPurgeAfterDays`.
 *
 * @param backend Current memory backend identifier: "sqlite", "markdown", or "none".
 * @param autoSave Whether the agent auto-saves conversation context.
 * @param embeddingProvider Current embedding provider: "", "openai", or "anthropic".
 * @param retentionDays Memory retention period in days, or -1 for forever.
 * @param onBackendChanged Callback when the user selects a different backend.
 * @param onAutoSaveChanged Callback when the auto-save toggle changes.
 * @param onEmbeddingProviderChanged Callback when the embedding provider changes.
 * @param onRetentionDaysChanged Callback when the retention period changes.
 * @param modifier Modifier applied to the root scrollable [Column].
 */
@Composable
fun MemoryConfigFlow(
    backend: String,
    autoSave: Boolean,
    embeddingProvider: String,
    retentionDays: Int,
    onBackendChanged: (String) -> Unit,
    onAutoSaveChanged: (Boolean) -> Unit,
    onEmbeddingProviderChanged: (String) -> Unit,
    onRetentionDaysChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Memory Configuration",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(TitleSpacing))

        Text(
            text = "Configure how your agent remembers conversations.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(DescriptionSpacing))

        BACKEND_OPTIONS.forEach { option ->
            val isSelected = backend == option.id

            BackendOptionCard(
                option = option,
                isSelected = isSelected,
                onClick = { onBackendChanged(option.id) },
            )

            Spacer(modifier = Modifier.height(CardSpacing))
        }

        Spacer(modifier = Modifier.height(SectionSpacing))

        AutoSaveToggle(
            autoSave = autoSave,
            onAutoSaveChanged = onAutoSaveChanged,
        )

        Spacer(modifier = Modifier.height(SectionSpacing))

        EmbeddingProviderDropdown(
            embeddingProvider = embeddingProvider,
            onEmbeddingProviderChanged = onEmbeddingProviderChanged,
        )

        Spacer(modifier = Modifier.height(SectionSpacing))

        RetentionPicker(
            retentionDays = retentionDays,
            onRetentionDaysChanged = onRetentionDaysChanged,
        )
    }
}

/**
 * A single selectable memory backend card.
 *
 * Applies primary-container background and primary border when selected.
 * Accessibility semantics include the option name, selection state, and a
 * radio-button role.
 *
 * @param option The backend option to display.
 * @param isSelected Whether this option is currently selected.
 * @param onClick Callback invoked when the card is tapped.
 */
@Composable
private fun BackendOptionCard(
    option: BackendOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors =
            if (isSelected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
        border =
            if (isSelected) {
                BorderStroke(
                    SelectedBorderWidth,
                    MaterialTheme.colorScheme.primary,
                )
            } else {
                null
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
        Column(
            modifier = Modifier.padding(CardPadding),
        ) {
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

/**
 * Auto-save toggle row with label and [Switch].
 *
 * The entire row is a single accessibility element announcing both the label
 * and the current toggle state.
 *
 * @param autoSave Whether auto-save is currently enabled.
 * @param onAutoSaveChanged Callback when the switch state changes.
 */
@Composable
private fun AutoSaveToggle(
    autoSave: Boolean,
    onAutoSaveChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "Auto-save conversations, ${if (autoSave) "on" else "off"}"
                },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto-save",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Automatically save conversation context",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(CardSpacing))
        Switch(
            checked = autoSave,
            onCheckedChange = onAutoSaveChanged,
        )
    }
}

/**
 * Embedding provider dropdown using Material 3 [ExposedDropdownMenuBox].
 *
 * Shows the currently selected provider in a read-only [OutlinedTextField]
 * and expands a menu of available embedding providers on click.
 *
 * @param embeddingProvider Current embedding provider identifier.
 * @param onEmbeddingProviderChanged Callback when a different provider is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddingProviderDropdown(
    embeddingProvider: String,
    onEmbeddingProviderChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentDisplay =
        EMBEDDING_OPTIONS
            .find { it.id == embeddingProvider }
            ?.displayName ?: "None"

    Text(
        text = "Embedding Provider",
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(LabelSpacing))

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentDisplay,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .semantics {
                        contentDescription = "Embedding provider: $currentDisplay"
                    },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            EMBEDDING_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onEmbeddingProviderChanged(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Retention period picker using selectable [FilterChip] options.
 *
 * Presents 7 days, 30 days, 90 days, and Forever as chip choices arranged in
 * a flow row. Each chip is styled with a selected indicator when active.
 *
 * @param retentionDays Current retention period in days, or -1 for forever.
 * @param onRetentionDaysChanged Callback when the user selects a different period.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetentionPicker(
    retentionDays: Int,
    onRetentionDaysChanged: (Int) -> Unit,
) {
    Text(
        text = "Retention Period",
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(LabelSpacing))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        RETENTION_OPTIONS.forEach { option ->
            val isSelected = retentionDays == option.days

            FilterChip(
                selected = isSelected,
                onClick = { onRetentionDaysChanged(option.days) },
                label = { Text(option.label) },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "${option.label}, ${if (isSelected) "selected" else "not selected"}"
                        role = Role.RadioButton
                    },
            )
        }
    }
}

@Preview(name = "Memory Config - SQLite")
@Composable
private fun PreviewSqlite() {
    ZeroClawTheme {
        Surface {
            MemoryConfigFlow(
                backend = "sqlite",
                autoSave = true,
                embeddingProvider = "",
                retentionDays = 30,
                onBackendChanged = {},
                onAutoSaveChanged = {},
                onEmbeddingProviderChanged = {},
                onRetentionDaysChanged = {},
            )
        }
    }
}

@Preview(name = "Memory Config - Markdown with Embeddings")
@Composable
private fun PreviewMarkdown() {
    ZeroClawTheme {
        Surface {
            MemoryConfigFlow(
                backend = "markdown",
                autoSave = false,
                embeddingProvider = "openai",
                retentionDays = 90,
                onBackendChanged = {},
                onAutoSaveChanged = {},
                onEmbeddingProviderChanged = {},
                onRetentionDaysChanged = {},
            )
        }
    }
}

@Preview(name = "Memory Config - None Forever")
@Composable
private fun PreviewNone() {
    ZeroClawTheme {
        Surface {
            MemoryConfigFlow(
                backend = "none",
                autoSave = false,
                embeddingProvider = "",
                retentionDays = RETENTION_FOREVER,
                onBackendChanged = {},
                onAutoSaveChanged = {},
                onEmbeddingProviderChanged = {},
                onRetentionDaysChanged = {},
            )
        }
    }
}

@Preview(
    name = "Memory Config - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            MemoryConfigFlow(
                backend = "sqlite",
                autoSave = true,
                embeddingProvider = "anthropic",
                retentionDays = 7,
                onBackendChanged = {},
                onAutoSaveChanged = {},
                onEmbeddingProviderChanged = {},
                onRetentionDaysChanged = {},
            )
        }
    }
}
