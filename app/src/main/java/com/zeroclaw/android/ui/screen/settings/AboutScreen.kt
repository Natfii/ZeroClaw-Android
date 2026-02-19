/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.BuildConfig
import com.zeroclaw.android.ui.component.SectionHeader
import com.zeroclaw.ffi.getVersion

/**
 * About screen displaying app version, licenses, and project links.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun AboutScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var crateVersion by remember { mutableStateOf(CRATE_VERSION_FALLBACK) }

    LaunchedEffect(Unit) {
        @Suppress("TooGenericExceptionCaught")
        try {
            crateVersion = getVersion()
        } catch (_: Exception) {
            crateVersion = CRATE_VERSION_FALLBACK
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Version")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AboutRow(label = "App Version", value = BuildConfig.VERSION_NAME)
                AboutRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
                AboutRow(label = "Crate Version", value = crateVersion)
            }
        }

        SectionHeader(title = "Links")
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)),
                )
            },
        ) {
            Text("View on GitHub")
        }
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(LICENSE_URL)),
                )
            },
        ) {
            Text("View License (MIT)")
        }

        SectionHeader(title = "Credits")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Built with ZeroClaw, a Rust-native AI agent framework.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Accessible label-value row for the about screen.
 *
 * Uses [semantics] with [mergeDescendants] so screen readers announce
 * the label and value as a single phrase.
 *
 * @param label Description of the value.
 * @param value The displayed value string.
 */
@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val CRATE_VERSION_FALLBACK = "unknown"
private const val GITHUB_URL = "https://github.com/Natfii/ZeroClaw-Android"
private const val LICENSE_URL = "https://github.com/Natfii/ZeroClaw-Android/blob/main/LICENSE"
