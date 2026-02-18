/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.ProviderRegistry
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.KeyStatus

/** Icon size for provider icons inside connection cards. */
private const val CONNECTION_ICON_SIZE_DP = 40

/** Status dot diameter. */
private const val STATUS_DOT_SIZE_DP = 8

/** Spacing between connection cards. */
private const val CARD_SPACING_DP = 8

/** Internal padding for connection cards. */
private const val CARD_PADDING_DP = 12

/** Spacing between the icon and text content. */
private const val ICON_TEXT_SPACING_DP = 12

/** Spacing between the status dot and its label. */
private const val DOT_LABEL_SPACING_DP = 4

/** Border width for the selected card. */
private const val SELECTED_BORDER_WIDTH_DP = 2

/**
 * Reusable section displaying stored API keys as selectable connection cards.
 *
 * Each card shows the provider icon, name, masked key, and status. The
 * selected card is highlighted with a primary-colored border and tinted
 * background. A trailing button allows adding new connections without
 * leaving the screen.
 *
 * @param keys All stored API keys to display.
 * @param selectedKeyId The ID of the currently selected key, or null.
 * @param onKeySelected Callback when the user taps a connection card.
 * @param onAddNewConnection Callback when the user taps the add button.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ConnectionPickerSection(
    keys: List<ApiKey>,
    selectedKeyId: String?,
    onKeySelected: (ApiKey) -> Unit,
    onAddNewConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(title = "Connection")
        Spacer(modifier = Modifier.height(CARD_SPACING_DP.dp))

        if (keys.isEmpty()) {
            Text(
                text = "No API keys configured yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CARD_SPACING_DP.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp)) {
                keys.forEach { key ->
                    ConnectionCard(
                        apiKey = key,
                        isSelected = key.id == selectedKeyId,
                        onClick = { onKeySelected(key) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(CARD_SPACING_DP.dp))
        }

        OutlinedButton(
            onClick = onAddNewConnection,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Add new API key connection" },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(CARD_SPACING_DP.dp))
            Text("New Connection")
        }
    }
}

/**
 * A single selectable connection card showing provider info and key status.
 *
 * @param apiKey The API key to display.
 * @param isSelected Whether this card is currently selected.
 * @param onClick Callback when the card is tapped.
 */
@Composable
private fun ConnectionCard(
    apiKey: ApiKey,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val displayName =
        ProviderRegistry.findById(apiKey.provider)?.displayName
            ?: apiKey.provider
    val selectionLabel = if (isSelected) "Selected" else "Not selected"

    OutlinedCard(
        onClick = onClick,
        border =
            if (isSelected) {
                BorderStroke(
                    SELECTED_BORDER_WIDTH_DP.dp,
                    MaterialTheme.colorScheme.primary,
                )
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "$displayName connection, $selectionLabel"
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.secondaryContainer,
                            )
                        } else {
                            Modifier
                        },
                    ).padding(CARD_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(
                provider = apiKey.provider,
                modifier = Modifier.size(CONNECTION_ICON_SIZE_DP.dp),
            )
            Spacer(modifier = Modifier.width(ICON_TEXT_SPACING_DP.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                )
                MaskedText(
                    text = apiKey.key,
                    revealed = false,
                )
            }
            Spacer(modifier = Modifier.width(ICON_TEXT_SPACING_DP.dp))
            KeyStatusIndicator(status = apiKey.status)
        }
    }
}

/**
 * Small status dot with label for an API key's [KeyStatus].
 *
 * @param status The key's current validation status.
 */
@Composable
private fun KeyStatusIndicator(status: KeyStatus) {
    val (color, label) =
        when (status) {
            KeyStatus.ACTIVE ->
                MaterialTheme.colorScheme.primary to "Active"
            KeyStatus.INVALID ->
                MaterialTheme.colorScheme.error to "Invalid"
            KeyStatus.UNKNOWN ->
                MaterialTheme.colorScheme.outline to "Unknown"
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(STATUS_DOT_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(DOT_LABEL_SPACING_DP.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
