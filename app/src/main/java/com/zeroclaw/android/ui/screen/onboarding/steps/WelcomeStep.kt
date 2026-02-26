/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import android.content.res.Configuration
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.R
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Size of the hero image at the top of the welcome screen. */
private val HeroImageSize = 160.dp

/** Spacing after the hero icon. */
private val HeroSpacing = 24.dp

/** Spacing after the title. */
private val TitleSpacing = 16.dp

/** Spacing after the description. */
private val DescriptionSpacing = 32.dp

/** Internal padding of the setup preview card. */
private val CardPadding = 16.dp

/** Spacing after the card title. */
private val CardTitleSpacing = 12.dp

/** Spacing between checklist items. */
private val CheckItemSpacing = 8.dp

/** Size of the checkmark icon in each checklist item. */
private val CheckIconSize = 20.dp

/** Spacing between the checkmark icon and its label. */
private val CheckTextSpacing = 8.dp

/** Spacing after the setup preview card. */
private val CardBottomSpacing = 24.dp

/**
 * Welcome step introducing the user to ZeroClaw.
 *
 * Renders a scrollable column with a hero icon, title, description,
 * a setup preview card listing what the wizard will configure, and
 * a hint to proceed.
 *
 * @param modifier Modifier applied to the root scrollable [Column].
 */
@Composable
fun WelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.zero_crab),
            contentDescription = "Zero the crab, ZeroClaw mascot",
            modifier = Modifier.size(HeroImageSize),
        )

        Spacer(modifier = Modifier.height(HeroSpacing))

        Text(
            text = "Welcome to ZeroClaw",
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(TitleSpacing))

        Text(
            text =
                "ZeroClaw is an AI agent that runs on your device as an " +
                    "always-on service. It connects to messaging platforms " +
                    "like Telegram and Discord, and can be customized with " +
                    "tools, memory, and scheduled tasks.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(DescriptionSpacing))

        SetupPreviewCard(
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(CardBottomSpacing))

        Text(
            text = "Tap Next to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Card previewing what the setup wizard will configure.
 *
 * Lists four configuration areas as checklist items inside an
 * [ElevatedCard] with a descriptive title.
 *
 * @param modifier Modifier applied to the [ElevatedCard].
 */
@Composable
private fun SetupPreviewCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = "Setup wizard overview"
            },
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = "This wizard will set up:",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(CardTitleSpacing))

            SetupCheckItem(label = "AI provider and model")
            Spacer(modifier = Modifier.height(CheckItemSpacing))
            SetupCheckItem(label = "Messaging channels")
            Spacer(modifier = Modifier.height(CheckItemSpacing))
            SetupCheckItem(label = "Memory and identity")
            Spacer(modifier = Modifier.height(CheckItemSpacing))
            SetupCheckItem(label = "Security settings")
        }
    }
}

/**
 * A single checklist item with a leading checkmark icon.
 *
 * @param label The description text for this setup item.
 */
@Composable
private fun SetupCheckItem(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CheckIconSize),
        )
        Spacer(modifier = Modifier.width(CheckTextSpacing))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(name = "Welcome Step")
@Composable
private fun PreviewWelcomeStep() {
    ZeroClawTheme {
        Surface {
            WelcomeStep()
        }
    }
}

@Preview(
    name = "Welcome Step - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewWelcomeStepDark() {
    ZeroClawTheme {
        Surface {
            WelcomeStep()
        }
    }
}
