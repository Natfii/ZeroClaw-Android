/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Onboarding step for basic agent configuration.
 *
 * Allows the user to set a name for their first agent. The name is
 * propagated to [com.zeroclaw.android.ui.screen.onboarding.OnboardingViewModel]
 * and persisted when onboarding completes.
 *
 * @param agentName Current agent name value.
 * @param onAgentNameChanged Callback when the user edits the name.
 */
@Composable
fun AgentConfigStep(
    agentName: String,
    onAgentNameChanged: (String) -> Unit,
) {
    Column {
        Text(
            text = "Configure Agent",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text =
                "Set up your first AI agent. " +
                    "You can customize it further and add more agents later.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = agentName,
            onValueChange = onAgentNameChanged,
            label = { Text("Agent Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "The default model will be used. You can change this in agent settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
