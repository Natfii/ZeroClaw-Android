/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONException
import org.json.JSONObject

/** Minimum height for the JSON text field. */
private const val JSON_FIELD_MIN_LINES = 8

/** Minimum touch target height. */
private const val MIN_TOUCH_TARGET_DP = 48

/** Spacing after section headers. */
private const val SECTION_SPACING_DP = 16

/**
 * Full-screen AIEOS identity JSON editor.
 *
 * Allows the user to paste or edit an AIEOS v1.1 identity document.
 * The document is stored as a raw JSON string in [AppSettings.identityJson]
 * and embedded inline in the TOML `[identity]` section.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param settingsViewModel The shared [SettingsViewModel].
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun IdentityScreen(
    edgeMargin: Dp,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var jsonText by remember(settings.identityJson) {
        mutableStateOf(settings.identityJson)
    }
    val jsonError by remember {
        derivedStateOf {
            if (jsonText.isBlank()) {
                null
            } else {
                try {
                    JSONObject(jsonText)
                    null
                } catch (_: JSONException) {
                    "Invalid JSON format"
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        Text(
            text = "Agent Identity",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        Text(
            text =
                "Paste an AIEOS v1.1 identity JSON document below. " +
                    "This defines your agent's personality, name, and capabilities " +
                    "for upstream identity resolution.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        OutlinedTextField(
            value = jsonText,
            onValueChange = { jsonText = it },
            label = { Text("AIEOS JSON") },
            isError = jsonError != null,
            supportingText = jsonError?.let { msg -> { Text(msg) } },
            minLines = JSON_FIELD_MIN_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        FilledTonalButton(
            onClick = { settingsViewModel.updateIdentityJson(jsonText) },
            enabled = jsonError == null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MIN_TOUCH_TARGET_DP.dp)
                    .semantics { contentDescription = "Save identity" },
        ) {
            Text("Save")
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
    }
}
