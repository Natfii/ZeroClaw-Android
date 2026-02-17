/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.ChannelFieldSpec
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.FieldInputType
import com.zeroclaw.android.ui.component.CollapsibleSection
import com.zeroclaw.android.ui.screen.settings.apikeys.SaveState
import java.util.UUID

/** Spacing between form fields. */
private const val FIELD_SPACING_DP = 12

/** Heading spacing. */
private const val HEADING_SPACING_DP = 16

/** Bottom spacing. */
private const val BOTTOM_SPACING_DP = 24

/** Advanced section threshold: channels with more fields than this get a collapsible section. */
private const val ADVANCED_THRESHOLD = 4

/**
 * Dynamic form screen for configuring a connected channel.
 *
 * Fields are rendered based on the [ChannelType.fields] specification.
 * Secret fields use password visual transformation with a reveal toggle.
 * Optional fields beyond the [ADVANCED_THRESHOLD] are grouped in a
 * collapsible "Advanced" section.
 *
 * @param channelId Existing channel ID for editing, or null for new.
 * @param channelTypeName Channel type name for new channel creation.
 * @param onSaved Callback invoked after saving.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param channelsViewModel The [ChannelsViewModel] for persistence.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ChannelDetailScreen(
    channelId: String?,
    channelTypeName: String?,
    onSaved: () -> Unit,
    edgeMargin: Dp,
    channelsViewModel: ChannelsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val saveState by channelsViewModel.saveState.collectAsStateWithLifecycle()
    val channelType = remember(channelId, channelTypeName) {
        if (channelTypeName != null) {
            runCatching { ChannelType.valueOf(channelTypeName) }.getOrNull()
        } else {
            null
        }
    }

    var loadedChannel by remember { mutableStateOf<ConnectedChannel?>(null) }
    var resolvedType by remember { mutableStateOf(channelType) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(channelId == null) }

    LaunchedEffect(channelId) {
        if (channelId != null) {
            val result = channelsViewModel.loadChannelWithSecrets(channelId)
            if (result != null) {
                loadedChannel = result.first
                resolvedType = result.first.type
                fieldValues.putAll(result.second)
            }
            loaded = true
        } else if (channelType != null) {
            channelType.fields.forEach { spec ->
                if (spec.defaultValue.isNotEmpty()) {
                    fieldValues[spec.key] = spec.defaultValue
                }
            }
        }
    }

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            channelsViewModel.resetSaveState()
            onSaved()
        }
    }

    val currentType = resolvedType ?: return
    if (!loaded) return

    val requiredFields = currentType.fields.filter { it.isRequired }
    val optionalFields = currentType.fields.filter { !it.isRequired }
    val hasAdvanced = optionalFields.size > ADVANCED_THRESHOLD

    val allRequiredFilled = requiredFields.all { spec ->
        fieldValues[spec.key]?.isNotBlank() == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = edgeMargin)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(HEADING_SPACING_DP.dp))

        Text(
            text = if (channelId != null) {
                "Edit ${currentType.displayName}"
            } else {
                "Add ${currentType.displayName}"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(HEADING_SPACING_DP.dp))

        requiredFields.forEach { spec ->
            ChannelField(
                spec = spec,
                value = fieldValues[spec.key].orEmpty(),
                onValueChange = { fieldValues[spec.key] = it },
            )
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
        }

        if (hasAdvanced) {
            CollapsibleSection(title = "Advanced") {
                optionalFields.forEach { spec ->
                    ChannelField(
                        spec = spec,
                        value = fieldValues[spec.key].orEmpty(),
                        onValueChange = { fieldValues[spec.key] = it },
                    )
                    Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
                }
            }
        } else {
            optionalFields.forEach { spec ->
                ChannelField(
                    spec = spec,
                    value = fieldValues[spec.key].orEmpty(),
                    onValueChange = { fieldValues[spec.key] = it },
                )
                Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
            }
        }

        Spacer(modifier = Modifier.height(BOTTOM_SPACING_DP.dp))

        FilledTonalButton(
            onClick = {
                val channel = loadedChannel?.copy(
                    configValues = emptyMap(),
                ) ?: ConnectedChannel(
                    id = UUID.randomUUID().toString(),
                    type = currentType,
                )
                channelsViewModel.saveChannel(channel, fieldValues.toMap())
            },
            enabled = allRequiredFilled && saveState !is SaveState.Saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (channelId != null) "Save Changes" else "Add Channel")
        }

        if (saveState is SaveState.Error) {
            Spacer(modifier = Modifier.height(FIELD_SPACING_DP.dp))
            Text(
                text = (saveState as SaveState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(BOTTOM_SPACING_DP.dp))
    }
}

/**
 * Renders a single channel configuration field based on its [ChannelFieldSpec].
 *
 * @param spec The field specification.
 * @param value Current field value.
 * @param onValueChange Callback when the value changes.
 */
@Composable
private fun ChannelField(
    spec: ChannelFieldSpec,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (spec.inputType) {
        FieldInputType.BOOLEAN -> {
            BooleanField(
                label = spec.label,
                checked = value.lowercase() == "true",
                onCheckedChange = { onValueChange(it.toString()) },
            )
        }
        FieldInputType.SECRET -> {
            SecretField(
                label = spec.label,
                value = value,
                onValueChange = onValueChange,
                isRequired = spec.isRequired,
            )
        }
        else -> {
            val keyboardType = when (spec.inputType) {
                FieldInputType.NUMBER -> KeyboardType.Number
                FieldInputType.URL -> KeyboardType.Uri
                else -> KeyboardType.Text
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Text(
                        if (spec.isRequired) "${spec.label} *" else spec.label,
                    )
                },
                supportingText = if (spec.inputType == FieldInputType.LIST) {
                    { Text("Comma-separated values") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A boolean toggle field rendered as a labeled switch.
 *
 * @param label Human-readable label.
 * @param checked Current toggle state.
 * @param onCheckedChange Callback when toggled.
 */
@Composable
private fun BooleanField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = "$label ${if (checked) "enabled" else "disabled"}"
            },
        )
    }
}

/**
 * A secret text field with password masking and a reveal toggle.
 *
 * @param label Human-readable label.
 * @param value Current field value.
 * @param onValueChange Callback when text changes.
 * @param isRequired Whether the field is required.
 */
@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isRequired: Boolean,
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (isRequired) "$label *" else label) },
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = if (revealed) "Hide $label" else "Show $label",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
