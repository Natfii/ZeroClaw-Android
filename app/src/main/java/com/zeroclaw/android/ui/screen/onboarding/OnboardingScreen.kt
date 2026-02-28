/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

@file:Suppress("MatchingDeclarationName")

package com.zeroclaw.android.ui.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.data.channel.ChannelSetupSpecs
import com.zeroclaw.android.ui.component.setup.ChannelSelectionGrid
import com.zeroclaw.android.ui.component.setup.ChannelSetupFlow
import com.zeroclaw.android.ui.component.setup.IdentityConfigFlow
import com.zeroclaw.android.ui.component.setup.MemoryConfigFlow
import com.zeroclaw.android.ui.component.setup.TunnelConfigFlow
import com.zeroclaw.android.ui.screen.onboarding.state.ChannelSubFlowState
import com.zeroclaw.android.ui.screen.onboarding.steps.ActivationStep
import com.zeroclaw.android.ui.screen.onboarding.steps.PermissionsStep
import com.zeroclaw.android.ui.screen.onboarding.steps.ProviderStep
import com.zeroclaw.android.ui.screen.onboarding.steps.SecurityStep
import com.zeroclaw.android.ui.screen.onboarding.steps.WelcomeStep

/** Spacing between the progress indicator and the step label. */
private val ProgressLabelSpacing = 8.dp

/** Spacing between the step label and the step content. */
private val LabelContentSpacing = 24.dp

/** Outer padding for the onboarding screen content. */
private val ScreenPadding = 24.dp

/** Minimum touch target height for navigation buttons (WCAG AA). */
private val MinButtonHeight = 48.dp

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
 * Thin stateful wrapper that collects [OnboardingCoordinator] flows and
 * delegates rendering to [OnboardingContent]. Supports nine steps:
 * permissions, welcome, provider, channels, tunnel, security, memory,
 * identity, and activation.
 *
 * @param onComplete Callback invoked when onboarding finishes.
 * @param coordinator The [OnboardingCoordinator] for step management.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    coordinator: OnboardingCoordinator = viewModel(),
    modifier: Modifier = Modifier,
) {
    val currentStep by coordinator.currentStep.collectAsStateWithLifecycle()
    val activationState by coordinator.activationState.collectAsStateWithLifecycle()
    val channelSelectionState by coordinator.channelSelectionState.collectAsStateWithLifecycle()
    val totalSteps = coordinator.totalSteps
    val snackbarHostState = remember { SnackbarHostState() }
    val hasActiveSubFlow = channelSelectionState.activeSubFlowType != null

    LaunchedEffect(activationState.completeError) {
        val error = activationState.completeError ?: return@LaunchedEffect
        coordinator.dismissCompleteError()
        snackbarHostState.showSnackbar(error)
    }

    val onActivate: () -> Unit = { coordinator.complete(onDone = onComplete) }

    OnboardingContent(
        state =
            OnboardingState(
                currentStep = currentStep,
                totalSteps = totalSteps,
                isCompleting = activationState.isCompleting,
            ),
        snackbarHostState = snackbarHostState,
        onNextStep = {
            if (hasActiveSubFlow) {
                coordinator.exitChannelSubFlow()
            } else {
                coordinator.nextStep()
            }
        },
        onPreviousStep = {
            if (hasActiveSubFlow) {
                coordinator.exitChannelSubFlow()
            } else {
                coordinator.previousStep()
            }
        },
        stepContent = { step ->
            when (step) {
                OnboardingCoordinator.STEP_PERMISSIONS ->
                    PermissionsStepCollector(coordinator)
                OnboardingCoordinator.STEP_WELCOME ->
                    WelcomeStep()
                OnboardingCoordinator.STEP_PROVIDER ->
                    ProviderStepCollector(coordinator)
                OnboardingCoordinator.STEP_CHANNELS ->
                    ChannelStepCollector(coordinator)
                OnboardingCoordinator.STEP_TUNNEL ->
                    TunnelStepCollector(coordinator)
                OnboardingCoordinator.STEP_SECURITY ->
                    SecurityStepCollector(coordinator)
                OnboardingCoordinator.STEP_MEMORY ->
                    MemoryStepCollector(coordinator)
                OnboardingCoordinator.STEP_IDENTITY ->
                    IdentityStepCollector(coordinator)
                OnboardingCoordinator.STEP_ACTIVATION ->
                    ActivationStepCollector(
                        coordinator = coordinator,
                        onActivate = onActivate,
                    )
                else -> Unit
            }
        },
        modifier = modifier,
    )
}

/**
 * Stateless onboarding content composable for testing.
 *
 * Renders a [Scaffold] with a linear progress indicator, step label,
 * step content area, and bottom navigation buttons. The activation step
 * renders its own action button via the [stepContent] slot.
 *
 * @param state Aggregated onboarding state snapshot.
 * @param snackbarHostState Snackbar host state for error messages.
 * @param onNextStep Callback to advance to the next step.
 * @param onPreviousStep Callback to go back to the previous step.
 * @param stepContent Slot for rendering the current step content.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    snackbarHostState: SnackbarHostState,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
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
                    .padding(ScreenPadding),
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

            Spacer(modifier = Modifier.height(ProgressLabelSpacing))

            Text(
                text = "Step ${state.currentStep + 1} of ${state.totalSteps}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(LabelContentSpacing))

            Column(modifier = Modifier.weight(1f)) {
                stepContent(state.currentStep)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.currentStep > 0) {
                    OutlinedButton(
                        onClick = onPreviousStep,
                        modifier = Modifier.defaultMinSize(minHeight = MinButtonHeight),
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                if (state.currentStep < state.totalSteps - 1) {
                    FilledTonalButton(
                        onClick = onNextStep,
                        modifier = Modifier.defaultMinSize(minHeight = MinButtonHeight),
                    ) {
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
 * @param coordinator The [OnboardingCoordinator] owning the lock state flows.
 */
@Composable
private fun PermissionsStepCollector(coordinator: OnboardingCoordinator) {
    val pinHash by coordinator.pinHash.collectAsStateWithLifecycle()

    PermissionsStep(
        pinHash = pinHash,
        onPinSet = coordinator::setPinHash,
    )
}

/**
 * Collects provider-related flows and delegates to [ProviderStep].
 *
 * Isolating the flow collections here prevents provider state changes from
 * recomposing the parent [OnboardingScreen] layout (progress bar, buttons).
 *
 * @param coordinator The [OnboardingCoordinator] owning the provider state flows.
 */
@Composable
private fun ProviderStepCollector(coordinator: OnboardingCoordinator) {
    val state by coordinator.providerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ProviderStep(
        selectedProvider = state.providerId,
        apiKey = state.apiKey,
        baseUrl = state.baseUrl,
        selectedModel = state.model,
        availableModels = state.availableModels,
        isLoadingModels = state.isLoadingModels,
        validationResult = state.validationResult,
        onProviderChanged = coordinator::setProvider,
        onApiKeyChanged = coordinator::setApiKey,
        onBaseUrlChanged = coordinator::setBaseUrl,
        onModelChanged = coordinator::setModel,
        onValidate = coordinator::validateProvider,
        isOAuthInProgress = state.isOAuthInProgress,
        oauthEmail = state.oauthEmail,
        onOAuthLogin = { coordinator.startOAuthLogin(context) },
        onOAuthDisconnect = { coordinator.disconnectOAuth() },
    )
}

/**
 * Collects channel selection and sub-flow states to render either the
 * [ChannelSelectionGrid] or a [ChannelSetupFlow] for the active channel.
 *
 * When a channel sub-flow is active, renders the per-channel guided wizard.
 * Otherwise shows the multi-select grid with configure buttons for each
 * selected channel.
 *
 * Isolating the flow collections here prevents channel state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the channel state flows.
 */
@Suppress("CognitiveComplexMethod", "LongMethod")
@Composable
private fun ChannelStepCollector(coordinator: OnboardingCoordinator) {
    val selectionState by coordinator.channelSelectionState.collectAsStateWithLifecycle()
    val subFlowStates by coordinator.channelSubFlowStates.collectAsStateWithLifecycle()

    val activeType = selectionState.activeSubFlowType
    if (activeType != null) {
        val spec = ChannelSetupSpecs.forType(activeType) ?: return
        val subFlowState = subFlowStates[activeType] ?: ChannelSubFlowState()
        ChannelSetupFlow(
            spec = spec,
            currentSubStep = subFlowState.currentSubStep,
            fieldValues = subFlowState.fieldValues,
            validationResult = subFlowState.validationResult,
            onFieldChanged = { key, value ->
                coordinator.setChannelField(activeType, key, value)
            },
            onValidate = { coordinator.validateChannel(activeType) },
            onNextSubStep = {
                if (subFlowState.currentSubStep >= spec.steps.size - 1) {
                    coordinator.exitChannelSubFlow()
                } else {
                    coordinator.nextChannelSubStep(activeType)
                }
            },
            onPreviousSubStep = {
                if (subFlowState.currentSubStep == 0) {
                    coordinator.exitChannelSubFlow()
                } else {
                    coordinator.previousChannelSubStep(activeType)
                }
            },
        )
    } else {
        Column {
            ChannelSelectionGrid(
                selectedTypes = selectionState.selectedTypes,
                configuredTypes =
                    subFlowStates
                        .filter { it.value.completed }
                        .keys,
                onSelectionChanged = { newSelection ->
                    val added = newSelection - selectionState.selectedTypes
                    val removed = selectionState.selectedTypes - newSelection
                    added.forEach { coordinator.toggleChannelSelection(it) }
                    removed.forEach { coordinator.toggleChannelSelection(it) }
                    added.firstOrNull()?.let { coordinator.startChannelSubFlow(it) }
                },
                showSkipHint = true,
            )

            if (selectionState.selectedTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LabelContentSpacing))

                selectionState.selectedTypes.forEach { type ->
                    val state = subFlowStates[type]
                    val label =
                        if (state?.completed == true) {
                            "Reconfigure ${type.displayName}"
                        } else {
                            "Configure ${type.displayName}"
                        }
                    FilledTonalButton(
                        onClick = { coordinator.startChannelSubFlow(type) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MinButtonHeight)
                                .semantics {
                                    contentDescription = label
                                },
                    ) {
                        Text(label)
                    }
                    Spacer(modifier = Modifier.height(ProgressLabelSpacing))
                }
            }
        }
    }
}

/**
 * Collects tunnel-related flows and delegates to [TunnelConfigFlow].
 *
 * Isolating the flow collections here prevents tunnel state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the tunnel state flows.
 */
@Composable
private fun TunnelStepCollector(coordinator: OnboardingCoordinator) {
    val state by coordinator.tunnelState.collectAsStateWithLifecycle()

    TunnelConfigFlow(
        tunnelType = state.tunnelType,
        tunnelToken = state.tunnelToken,
        customEndpoint = state.customEndpoint,
        onTunnelTypeChanged = coordinator::setTunnelType,
        onTunnelTokenChanged = coordinator::setTunnelToken,
        onCustomEndpointChanged = coordinator::setCustomEndpoint,
        showSkipHint = true,
    )
}

/**
 * Collects security-related flows and delegates to [SecurityStep].
 *
 * Isolating the flow collection here prevents security state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the security state flows.
 */
@Composable
private fun SecurityStepCollector(coordinator: OnboardingCoordinator) {
    val state by coordinator.securityState.collectAsStateWithLifecycle()

    SecurityStep(
        autonomyLevel = state.autonomyLevel,
        onAutonomyLevelChanged = coordinator::setAutonomyLevel,
    )
}

/**
 * Collects memory-related flows and delegates to [MemoryConfigFlow].
 *
 * Isolating the flow collections here prevents memory state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the memory state flows.
 */
@Composable
private fun MemoryStepCollector(coordinator: OnboardingCoordinator) {
    val state by coordinator.memoryState.collectAsStateWithLifecycle()

    MemoryConfigFlow(
        backend = state.backend,
        autoSave = state.autoSave,
        embeddingProvider = state.embeddingProvider,
        retentionDays = state.retentionDays,
        onBackendChanged = coordinator::setMemoryBackend,
        onAutoSaveChanged = coordinator::setAutoSave,
        onEmbeddingProviderChanged = coordinator::setEmbeddingProvider,
        onRetentionDaysChanged = coordinator::setRetentionDays,
    )
}

/**
 * Collects identity-related flows and delegates to [IdentityConfigFlow].
 *
 * Isolating the flow collections here prevents identity state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the identity state flows.
 */
@Composable
private fun IdentityStepCollector(coordinator: OnboardingCoordinator) {
    val state by coordinator.identityState.collectAsStateWithLifecycle()

    IdentityConfigFlow(
        agentName = state.agentName,
        userName = state.userName,
        timezone = state.timezone,
        communicationStyle = state.communicationStyle,
        identityFormat = state.identityFormat,
        onAgentNameChanged = coordinator::setAgentName,
        onUserNameChanged = coordinator::setUserName,
        onTimezoneChanged = coordinator::setTimezone,
        onCommunicationStyleChanged = coordinator::setCommunicationStyle,
        onIdentityFormatChanged = coordinator::setIdentityFormat,
    )
}

/**
 * Collects activation and config summary flows and delegates to [ActivationStep].
 *
 * Isolating the flow collections here prevents activation state changes from
 * recomposing the parent [OnboardingScreen] layout.
 *
 * @param coordinator The [OnboardingCoordinator] owning the config summary flows.
 * @param onActivate Callback invoked when the user taps the activation button.
 */
@Composable
private fun ActivationStepCollector(
    coordinator: OnboardingCoordinator,
    onActivate: () -> Unit,
) {
    val summary by coordinator.configSummary.collectAsStateWithLifecycle()
    val activationState by coordinator.activationState.collectAsStateWithLifecycle()

    ActivationStep(
        configSummary = summary,
        onActivate = onActivate,
        isActivating = activationState.isCompleting,
    )
}
