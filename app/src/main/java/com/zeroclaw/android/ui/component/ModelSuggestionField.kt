/*
 * Copyright 2026 ZeroClaw Contributors
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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

/**
 * Editable text field with dropdown suggestions for model name entry.
 *
 * Shows suggested models from [ProviderInfo.suggestedModels][com.zeroclaw.android.model.ProviderInfo.suggestedModels]
 * as a dropdown while allowing the user to type a custom model name.
 *
 * @param value Current text value.
 * @param onValueChanged Callback invoked when text changes or a suggestion is selected.
 * @param suggestions List of suggested model names to display in the dropdown.
 * @param modifier Modifier applied to the root layout.
 * @param label Text label for the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSuggestionField(
    value: String,
    onValueChanged: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Model",
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isBlank()) {
            suggestions
        } else {
            suggestions.filter { it.lowercase().contains(value.lowercase()) }
        }
    }

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
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            singleLine = true,
            modifier = Modifier
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
