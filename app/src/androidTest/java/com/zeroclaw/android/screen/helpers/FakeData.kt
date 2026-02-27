/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen.helpers

import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.CheckStatus
import com.zeroclaw.android.model.CostSummary
import com.zeroclaw.android.model.CronJob
import com.zeroclaw.android.model.DaemonStatus
import com.zeroclaw.android.model.DiagnosticCategory
import com.zeroclaw.android.model.DiagnosticCheck
import com.zeroclaw.android.model.DoctorSummary
import com.zeroclaw.android.model.HealthDetail
import com.zeroclaw.android.model.Plugin
import com.zeroclaw.android.model.PluginCategory
import com.zeroclaw.android.model.ProcessedImage
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.ui.screen.agents.AgentsState
import com.zeroclaw.android.ui.screen.dashboard.DashboardState
import com.zeroclaw.android.ui.screen.onboarding.OnboardingState
import com.zeroclaw.android.ui.screen.plugins.PluginsState
import com.zeroclaw.android.ui.screen.plugins.SyncUiState
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeysState
import com.zeroclaw.android.ui.screen.settings.doctor.DoctorState
import com.zeroclaw.android.ui.screen.terminal.TerminalBlock
import com.zeroclaw.android.ui.screen.terminal.TerminalState
import com.zeroclaw.android.viewmodel.DaemonUiState

/**
 * Creates a default [DashboardState] for screen testing.
 *
 * @return A dashboard state with the daemon stopped and empty collections.
 */
internal fun fakeDashboardState(): DashboardState =
    DashboardState(
        serviceState = ServiceState.STOPPED,
        statusState = DaemonUiState.Idle,
        keyRejection = null,
        healthDetail = null,
        costSummary = null,
        cronJobs = emptyList(),
        enabledAgentCount = 0,
        installedPluginCount = 0,
        daemonStatus = null,
        activityEvents = emptyList(),
    )

/**
 * Creates a [DashboardState] with a running daemon for testing.
 *
 * @return A dashboard state simulating an active daemon with health data.
 */
internal fun fakeRunningDashboardState(): DashboardState =
    DashboardState(
        serviceState = ServiceState.RUNNING,
        statusState =
            DaemonUiState.Content(
                DaemonStatus(
                    running = true,
                    uptimeSeconds = 3600,
                    components = emptyMap(),
                ),
            ),
        keyRejection = null,
        healthDetail =
            HealthDetail(
                daemonRunning = true,
                pid = 12345,
                uptimeSeconds = 3600,
                components = emptyList(),
            ),
        costSummary =
            CostSummary(
                sessionCostUsd = 0.05,
                dailyCostUsd = 1.20,
                monthlyCostUsd = 15.0,
                totalTokens = 50000,
                requestCount = 25,
                modelBreakdownJson = "{}",
            ),
        cronJobs =
            listOf(
                CronJob(
                    id = "cron-1",
                    expression = "0 0/5 * * *",
                    command = "health-check",
                    nextRunMs = System.currentTimeMillis() + 300000,
                    lastRunMs = System.currentTimeMillis() - 300000,
                    lastStatus = "ok",
                    paused = false,
                    oneShot = false,
                ),
            ),
        enabledAgentCount = 2,
        installedPluginCount = 3,
        daemonStatus =
            DaemonStatus(
                running = true,
                uptimeSeconds = 3600,
                components = emptyMap(),
            ),
        activityEvents =
            listOf(
                ActivityEvent(
                    id = 1,
                    timestamp = System.currentTimeMillis(),
                    type = ActivityType.DAEMON_STARTED,
                    message = "Daemon started",
                ),
            ),
    )

/**
 * Creates a default [AgentsState] with sample agents.
 *
 * @return An agents state with two test agents.
 */
internal fun fakeAgentsState(): AgentsState =
    AgentsState(
        agents =
            listOf(
                Agent(
                    id = "agent-1",
                    name = "Test Agent",
                    provider = "OpenAI",
                    modelName = "gpt-4",
                    isEnabled = true,
                ),
                Agent(
                    id = "agent-2",
                    name = "Backup Agent",
                    provider = "Anthropic",
                    modelName = "claude-3",
                    isEnabled = false,
                ),
            ),
        searchQuery = "",
    )

/**
 * Creates a default [PluginsState] with sample plugins.
 *
 * @return A plugins state on the Installed tab with one plugin.
 */
internal fun fakePluginsState(): PluginsState =
    PluginsState(
        plugins =
            listOf(
                Plugin(
                    id = "plugin-1",
                    name = "HTTP Channel",
                    description = "REST API channel for ZeroClaw",
                    version = "1.0.0",
                    author = "ZeroClaw Labs",
                    category = PluginCategory.CHANNEL,
                    isInstalled = true,
                    isEnabled = true,
                ),
            ),
        selectedTab = 0,
        searchQuery = "",
        syncState = SyncUiState.Idle,
    )

/**
 * Creates a default [OnboardingState] at the first step.
 *
 * @return An onboarding state at step 0 of 9.
 */
internal fun fakeOnboardingState(): OnboardingState =
    OnboardingState(
        currentStep = 0,
        totalSteps = 9,
        isCompleting = false,
    )

/**
 * Creates a default [ApiKeysState] with sample keys.
 *
 * @return An API keys state with one active key.
 */
internal fun fakeApiKeysState(): ApiKeysState =
    ApiKeysState(
        keys =
            listOf(
                ApiKey(
                    id = "key-1",
                    provider = "OpenAI",
                    key = "sk-test-1234567890abcdef",
                ),
            ),
        revealedKeyId = null,
        corruptCount = 0,
        unusedKeyIds = emptySet(),
        unreachableKeyIds = emptySet(),
        storageHealth = StorageHealth.Healthy,
    )

/**
 * Creates a default [DoctorState] with sample check results.
 *
 * @return A doctor state with completed diagnostics.
 */
internal fun fakeDoctorState(): DoctorState =
    DoctorState(
        checks =
            listOf(
                DiagnosticCheck(
                    id = "check-1",
                    category = DiagnosticCategory.CONFIG,
                    title = "TOML Configuration",
                    status = CheckStatus.PASS,
                    detail = "Valid configuration found",
                ),
                DiagnosticCheck(
                    id = "check-2",
                    category = DiagnosticCategory.API_KEYS,
                    title = "API Key Present",
                    status = CheckStatus.PASS,
                    detail = "1 key configured",
                ),
                DiagnosticCheck(
                    id = "check-3",
                    category = DiagnosticCategory.CONNECTIVITY,
                    title = "Provider Reachable",
                    status = CheckStatus.WARN,
                    detail = "High latency detected",
                ),
            ),
        isRunning = false,
        summary =
            DoctorSummary(
                passCount = 2,
                warnCount = 1,
                failCount = 0,
            ),
    )

/**
 * Creates a sample [ProcessedImage] for testing.
 *
 * @return A processed image with placeholder data.
 */
internal fun fakeProcessedImage(): ProcessedImage =
    ProcessedImage(
        base64Data = "dGVzdA==",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        originalUri = "content://test/image1",
        displayName = "test-image.jpg",
    )

/**
 * Creates a default [TerminalState] with a welcome system block.
 *
 * @return A terminal state containing a single system block.
 */
internal fun fakeTerminalState(): TerminalState =
    TerminalState(
        blocks =
            listOf(
                TerminalBlock.System(
                    id = 1,
                    timestamp = System.currentTimeMillis(),
                    text = "ZeroClaw Terminal v0.0.29 \u2014 Type /help for commands",
                ),
            ),
        isLoading = false,
        pendingImages = emptyList(),
        isProcessingImages = false,
    )

/**
 * Creates a default [AppSettings] for testing.
 *
 * @return App settings with default values.
 */
internal fun fakeAppSettings(): AppSettings = AppSettings()
