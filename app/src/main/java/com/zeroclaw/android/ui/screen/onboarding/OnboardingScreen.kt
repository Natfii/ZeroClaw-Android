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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val completeError by onboardingViewModel.completeError.collectAsStateWithLifecycle()
    val isCompleting by onboardingViewModel.isCompleting.collectAsStateWithLifecycle()
    val totalSteps = onboardingViewModel.totalSteps
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(completeError) {
        val error = completeError ?: return@LaunchedEffect
        onboardingViewModel.dismissCompleteError()
        snackbarHostState.showSnackbar(error)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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

            Column(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    STEP_PERMISSIONS -> PermissionsStepCollector(onboardingViewModel)
                    STEP_PROVIDER -> ProviderStepCollector(onboardingViewModel)
                    STEP_AGENT_CONFIG -> AgentConfigStepCollector(onboardingViewModel)
                    STEP_CHANNELS -> ChannelSetupStepCollector(onboardingViewModel)
                    STEP_ACTIVATION ->
                        ActivationStep(
                            onActivate = {
                                onboardingViewModel.complete(onDone = onComplete)
                            },
                            isActivating = isCompleting,
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
}

/**
 * Collects biometric-related flows and delegates to [PermissionsStep].
 *
 * Isolating the flow collections here prevents biometric toggle changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param viewModel The [OnboardingViewModel] owning the biometric state flows.
 */
@Composable
private fun PermissionsStepCollector(viewModel: OnboardingViewModel) {
    val biometricForService by viewModel.biometricForService.collectAsStateWithLifecycle()
    val biometricForSettings by viewModel.biometricForSettings.collectAsStateWithLifecycle()

    PermissionsStep(
        biometricForService = biometricForService,
        biometricForSettings = biometricForSettings,
        onBiometricForServiceChanged = viewModel::setBiometricForService,
        onBiometricForSettingsChanged = viewModel::setBiometricForSettings,
    )
}

/**
 * Collects provider-related flows and delegates to [ProviderStep].
 *
 * Isolating the flow collections here prevents provider state changes from
 * recomposing the parent [OnboardingScreen] layout (progress bar, buttons).
 *
 * @param viewModel The [OnboardingViewModel] owning the provider state flows.
 */
@Composable
private fun ProviderStepCollector(viewModel: OnboardingViewModel) {
    val provider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val model by viewModel.selectedModel.collectAsStateWithLifecycle()

    ProviderStep(
        selectedProvider = provider,
        apiKey = apiKey,
        baseUrl = baseUrl,
        selectedModel = model,
        onProviderChanged = viewModel::setProvider,
        onApiKeyChanged = viewModel::setApiKey,
        onBaseUrlChanged = viewModel::setBaseUrl,
        onModelChanged = viewModel::setModel,
    )
}

/**
 * Collects the agent name flow and delegates to [AgentConfigStep].
 *
 * Isolating the flow collection here prevents agent name changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param viewModel The [OnboardingViewModel] owning the agent name flow.
 */
@Composable
private fun AgentConfigStepCollector(viewModel: OnboardingViewModel) {
    val agentName by viewModel.agentName.collectAsStateWithLifecycle()

    AgentConfigStep(
        agentName = agentName,
        onAgentNameChanged = viewModel::setAgentName,
    )
}

/**
 * Collects channel-related flows and delegates to [ChannelSetupStep].
 *
 * Isolating the flow collections here prevents channel state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param viewModel The [OnboardingViewModel] owning the channel state flows.
 */
@Composable
private fun ChannelSetupStepCollector(viewModel: OnboardingViewModel) {
    val channelType by viewModel.selectedChannelType.collectAsStateWithLifecycle()
    val channelFields by viewModel.channelFieldValues.collectAsStateWithLifecycle()

    ChannelSetupStep(
        selectedType = channelType,
        channelFieldValues = channelFields,
        onTypeSelected = viewModel::setChannelType,
        onFieldChanged = viewModel::setChannelField,
    )
}
