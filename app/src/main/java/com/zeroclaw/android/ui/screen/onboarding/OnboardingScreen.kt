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
 * Aggregated state for the onboarding content composable.
 *
 * @property currentStep Current step index.
 * @property totalSteps Total number of wizard steps.
 * @property isCompleting Whether the completion action is in progress.
 */
data class OnboardingState(
    val currentStep: Int,
    val totalSteps: Int,
    val isCompleting: Boolean,
)

/**
 * Onboarding wizard screen with step indicator and navigation buttons.
 *
 * Thin stateful wrapper that collects ViewModel flows and delegates
 * rendering to [OnboardingContent].
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

    OnboardingContent(
        state = OnboardingState(
            currentStep = currentStep,
            totalSteps = totalSteps,
            isCompleting = isCompleting,
        ),
        snackbarHostState = snackbarHostState,
        onComplete = onComplete,
        onNextStep = onboardingViewModel::nextStep,
        onPreviousStep = onboardingViewModel::previousStep,
        onActivate = { onboardingViewModel.complete(onDone = onComplete) },
        stepContent = { step ->
            when (step) {
                STEP_PERMISSIONS -> PermissionsStepCollector(onboardingViewModel)
                STEP_PROVIDER -> ProviderStepCollector(onboardingViewModel)
                STEP_AGENT_CONFIG -> AgentConfigStepCollector(onboardingViewModel)
                STEP_CHANNELS -> ChannelSetupStepCollector(onboardingViewModel)
            }
        },
        modifier = modifier,
    )
}

/**
 * Stateless onboarding content composable for testing.
 *
 * @param state Aggregated onboarding state snapshot.
 * @param snackbarHostState Snackbar host state for error messages.
 * @param onComplete Callback when onboarding finishes.
 * @param onNextStep Callback to advance to the next step.
 * @param onPreviousStep Callback to go back to the previous step.
 * @param onActivate Callback when the activation button is tapped.
 * @param stepContent Slot for rendering the current step content.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    snackbarHostState: SnackbarHostState,
    onComplete: () -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onActivate: () -> Unit,
    stepContent: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                progress = { (state.currentStep + 1).toFloat() / state.totalSteps },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Step ${state.currentStep + 1} of ${state.totalSteps}"
                        },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Step ${state.currentStep + 1} of ${state.totalSteps}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (state.currentStep == STEP_ACTIVATION) {
                    ActivationStep(
                        onActivate = onActivate,
                        isActivating = state.isCompleting,
                    )
                } else {
                    stepContent(state.currentStep)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.currentStep > 0) {
                    OutlinedButton(onClick = onPreviousStep) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                if (state.currentStep < state.totalSteps - 1) {
                    FilledTonalButton(onClick = onNextStep) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

/**
 * Collects lock-related flows and delegates to [PermissionsStep].
 *
 * Isolating the flow collections here prevents lock state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param viewModel The [OnboardingViewModel] owning the lock state flows.
 */
@Composable
private fun PermissionsStepCollector(viewModel: OnboardingViewModel) {
    val pinHash by viewModel.pinHash.collectAsStateWithLifecycle()
    val biometricUnlockEnabled by viewModel.biometricUnlockEnabled.collectAsStateWithLifecycle()

    PermissionsStep(
        pinHash = pinHash,
        biometricUnlockEnabled = biometricUnlockEnabled,
        onPinSet = viewModel::setPinHash,
        onBiometricUnlockEnabledChange = viewModel::setBiometricUnlockEnabled,
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
