/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.FieldInputType

/** Standard spacing between form fields. */
private const val FIELD_SPACING_DP = 12

/** Spacing after the section description. */
private const val DESCRIPTION_SPACING_DP = 16

/** Card padding in dp. */
private const val CARD_PADDING_DP = 12

/** Card spacing in dp. */
private const val CARD_SPACING_DP = 8

/** Border width for selected card. */
private const val SELECTED_BORDER_WIDTH_DP = 2

/**
 * Onboarding step for optional channel configuration.
 *
 * Shows a selectable list of the 9 channel types. When a type is selected,
 * displays the required fields only. Users can skip this step since channels
 * are optional for initial setup.
 *
 * @param selectedType Currently selected channel type, or null if none.
 * @param channelFieldValues Map of field key to entered value.
 * @param onTypeSelected Callback when a channel type is selected or deselected.
 * @param onFieldChanged Callback when a field value changes.
 */
@Composable
fun ChannelSetupStep(
    selectedType: ChannelType?,
    channelFieldValues: Map<String, String>,
    onTypeSelected: (ChannelType?) -> Unit,
    onFieldChanged: (String, String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Connect a Channel",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        Text(
            text =
                "Connect a chat platform so your agent can send and receive messages. " +
                    "You can skip this and add channels later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(DESCRIPTION_SPACING_DP.dp))

        ChannelType.entries.forEach { type ->
            val isSelected = selectedType == type
            Card(
                onClick = {
                    onTypeSelected(if (isSelected) null else type)
                },
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
                            SELECTED_BORDER_WIDTH_DP.dp,
                            MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(CARD_PADDING_DP.dp),
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
                Text(
                    text = "Required fields for ${type.displayName}:",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))

                type.fields.filter { it.isRequired }.forEach { spec ->
                    val keyboardType =
                        when (spec.inputType) {
                            FieldInputType.NUMBER -> KeyboardType.Number
                            FieldInputType.URL -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        }
                    OutlinedTextField(
                        value = channelFieldValues[spec.key].orEmpty(),
                        onValueChange = { onFieldChanged(spec.key, it) },
                        label = { Text("${spec.label} *") },
                        singleLine = true,
                        visualTransformation =
                            if (spec.isSecret) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
                }
            }

            Spacer(modifier = Modifier.height(CARD_SPACING_DP.dp))
        }

        Text(
            text = "You can skip this step and add channels later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
