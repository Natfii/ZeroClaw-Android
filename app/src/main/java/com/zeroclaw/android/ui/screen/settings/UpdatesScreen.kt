// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.component.SectionHeader

/** GitHub releases URL for the ZeroClaw-Android project. */
private const val RELEASES_URL = "https://github.com/Natfii/ZeroClaw-Android/releases"

/**
 * Updates screen with a manual update check button that opens the GitHub
 * releases page, and an informational note about future automatic update
 * checking. Version details are displayed on the About screen instead.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun UpdatesScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(title = "Check for Updates")

        ManualUpdateCard(
            onCheckForUpdates = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)),
                )
            },
        )

        SectionHeader(title = "Auto-check")

        AutoCheckInfoCard()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Card with a button that opens the GitHub releases page for manual update checking.
 *
 * The button launches an [Intent.ACTION_VIEW] intent targeting [RELEASES_URL].
 *
 * @param onCheckForUpdates Callback invoked when the user taps the button.
 */
@Composable
private fun ManualUpdateCard(onCheckForUpdates: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Visit the GitHub releases page to check for newer versions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onCheckForUpdates,
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription =
                                "Check for updates on GitHub"
                        },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = "Check for Updates")
            }
        }
    }
}

/**
 * Informational card explaining that automatic update checks are planned
 * for a future release and are not yet available.
 */
@Composable
private fun AutoCheckInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Automatic update checks are not yet available.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "A future release will add background update checking " +
                        "with configurable frequency and notification preferences.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
