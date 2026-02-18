/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.ui.screen.onboarding.steps.ActivationStep
import com.zeroclaw.android.ui.screen.onboarding.steps.AgentConfigStep
import com.zeroclaw.android.ui.screen.onboarding.steps.ChannelSetupStep
import com.zeroclaw.android.ui.screen.onboarding.steps.PermissionsStep
import com.zeroclaw.android.ui.screen.onboarding.steps.ProviderStep

/** Step index for the permissions setup step. */
private const val STEP_PERMISSIONS = 0

/** Step index for the provider / API key entry step. */
private const val STEP_PROVIDER = 1

/** Step index for the agent configuration step. */
private const val STEP_AGENT_CONFIG = 2

/** Step index for the channel setup step. */
private const val STEP_CHANNELS = 3

/** Step index for the final activation step. */
private const val STEP_ACTIVATION = 4

/**
 * Onboarding wizard screen with step indicator and navigation buttons.
 *
 * @param onComplete Callback invoked when onboarding finishes.
 * @param onboardingViewModel The [OnboardingViewModel] for step management.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onboardingViewModel: OnboardingViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val currentStep by onboardingViewModel.currentStep.collectAsStateWithLifecycle()
    val totalSteps = onboardingViewModel.totalSteps

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Step ${currentStep + 1} of $totalSteps"
                    },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Step ${currentStep + 1} of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        val providerState by onboardingViewModel.selectedProvider.collectAsStateWithLifecycle()
        val apiKeyState by onboardingViewModel.apiKey.collectAsStateWithLifecycle()
        val baseUrlState by onboardingViewModel.baseUrl.collectAsStateWithLifecycle()
        val modelState by onboardingViewModel.selectedModel.collectAsStateWithLifecycle()
        val agentNameState by onboardingViewModel.agentName.collectAsStateWithLifecycle()
        val channelTypeState by onboardingViewModel.selectedChannelType
            .collectAsStateWithLifecycle()
        val channelFieldsState by onboardingViewModel.channelFieldValues
            .collectAsStateWithLifecycle()

        Column(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                STEP_PERMISSIONS -> PermissionsStep()
                STEP_PROVIDER ->
                    ProviderStep(
                        selectedProvider = providerState,
                        apiKey = apiKeyState,
                        baseUrl = baseUrlState,
                        selectedModel = modelState,
                        onProviderChanged = onboardingViewModel::setProvider,
                        onApiKeyChanged = onboardingViewModel::setApiKey,
                        onBaseUrlChanged = onboardingViewModel::setBaseUrl,
                        onModelChanged = onboardingViewModel::setModel,
                    )
                STEP_AGENT_CONFIG ->
                    AgentConfigStep(
                        agentName = agentNameState,
                        onAgentNameChanged = onboardingViewModel::setAgentName,
                    )
                STEP_CHANNELS ->
                    ChannelSetupStep(
                        selectedType = channelTypeState,
                        channelFieldValues = channelFieldsState,
                        onTypeSelected = onboardingViewModel::setChannelType,
                        onFieldChanged = onboardingViewModel::setChannelField,
                    )
                STEP_ACTIVATION ->
                    ActivationStep(
                        onActivate = {
                            onboardingViewModel.complete(onDone = onComplete)
                        },
                    )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (currentStep > 0) {
                OutlinedButton(onClick = { onboardingViewModel.previousStep() }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier)
            }
            if (currentStep < totalSteps - 1) {
                FilledTonalButton(onClick = { onboardingViewModel.nextStep() }) {
                    Text("Next")
                }
            }
        }
    }
}
