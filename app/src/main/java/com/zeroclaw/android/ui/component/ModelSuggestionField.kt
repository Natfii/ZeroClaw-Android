/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.LocalPowerSaveMode

/** Size of the loading spinner shown while fetching live models. */
private const val SPINNER_SIZE_DP = 20

/** Stroke width of the loading spinner. */
private const val SPINNER_STROKE_DP = 2

/**
 * Editable text field with dropdown suggestions for model name entry.
 *
 * Shows either live model suggestions fetched from the provider's API or
 * static suggestions from [ProviderInfo.suggestedModels][com.zeroclaw.android.model.ProviderInfo.suggestedModels].
 * When using static data, supporting text indicates "Suggestions as of Feb 2026".
 *
 * @param value Current text value.
 * @param onValueChanged Callback invoked when text changes or a suggestion is selected.
 * @param suggestions Static fallback model suggestions from the provider registry.
 * @param modifier Modifier applied to the root layout.
 * @param label Text label for the field.
 * @param liveSuggestions Model names fetched live from the provider API.
 * @param isLoadingLive Whether live model data is currently being fetched.
 * @param isLiveData Whether [liveSuggestions] represents real-time data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSuggestionField(
    value: String,
    onValueChanged: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Model",
    liveSuggestions: List<String> = emptyList(),
    isLoadingLive: Boolean = false,
    isLiveData: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    val activeSuggestions =
        if (isLiveData && liveSuggestions.isNotEmpty()) {
            liveSuggestions
        } else {
            suggestions
        }

    val filteredSuggestions =
        remember(value, activeSuggestions) {
            if (value.isBlank()) {
                activeSuggestions
            } else {
                activeSuggestions.filter { it.lowercase().contains(value.lowercase()) }
            }
        }

    val showStaticHint = !isLiveData && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChanged(it)
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = {
                if (isLoadingLive) {
                    if (LocalPowerSaveMode.current) {
                        Text(
                            text = "\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SPINNER_SIZE_DP.dp),
                            strokeWidth = SPINNER_STROKE_DP.dp,
                        )
                    }
                } else if (activeSuggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            supportingText =
                if (showStaticHint) {
                    { Text("Suggestions as of Feb 2026") }
                } else {
                    null
                },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .semantics { contentDescription = "$label field with suggestions" },
        )

        if (filteredSuggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                filteredSuggestions.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onValueChanged(model)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
