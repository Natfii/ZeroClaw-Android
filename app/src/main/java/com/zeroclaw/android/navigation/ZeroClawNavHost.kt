/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zeroclaw.android.ui.screen.agents.AddAgentScreen
import com.zeroclaw.android.ui.screen.agents.AgentDetailScreen
import com.zeroclaw.android.ui.screen.agents.AgentsScreen
import com.zeroclaw.android.ui.screen.console.ConsoleScreen
import com.zeroclaw.android.ui.screen.dashboard.DashboardScreen
import com.zeroclaw.android.ui.screen.onboarding.OnboardingScreen
import com.zeroclaw.android.ui.screen.plugins.PluginDetailScreen
import com.zeroclaw.android.ui.screen.plugins.PluginsScreen
import com.zeroclaw.android.ui.screen.settings.AboutScreen
import com.zeroclaw.android.ui.screen.settings.AutonomyScreen
import com.zeroclaw.android.ui.screen.settings.BatterySettingsScreen
import com.zeroclaw.android.ui.screen.settings.GatewayScreen
import com.zeroclaw.android.ui.screen.settings.IdentityScreen
import com.zeroclaw.android.ui.screen.settings.MemoryAdvancedScreen
import com.zeroclaw.android.ui.screen.settings.ModelRoutesScreen
import com.zeroclaw.android.ui.screen.settings.ObservabilityScreen
import com.zeroclaw.android.ui.screen.settings.SchedulerScreen
import com.zeroclaw.android.ui.screen.settings.SecurityOverviewScreen
import com.zeroclaw.android.ui.screen.settings.ServiceConfigScreen
import com.zeroclaw.android.ui.screen.settings.SettingsScreen
import com.zeroclaw.android.ui.screen.settings.SettingsViewModel
import com.zeroclaw.android.ui.screen.settings.ToolManagementScreen
import com.zeroclaw.android.ui.screen.settings.TunnelScreen
import com.zeroclaw.android.ui.screen.settings.UpdatesScreen
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeyDetailScreen
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeysScreen
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeysViewModel
import com.zeroclaw.android.ui.screen.settings.channels.ChannelDetailScreen
import com.zeroclaw.android.ui.screen.settings.channels.ConnectedChannelsScreen
import com.zeroclaw.android.ui.screen.settings.doctor.DoctorScreen
import com.zeroclaw.android.ui.screen.settings.logs.LogViewerScreen

/**
 * Single [NavHost] mapping all route objects to their screen composables.
 *
 * @param navController Navigation controller managing the back stack.
 * @param startDestination Route object for the initial destination.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the [NavHost].
 */
@Composable
fun ZeroClawNavHost(
    navController: NavHostController,
    startDestination: Any,
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<DashboardRoute> {
            DashboardScreen(edgeMargin = edgeMargin)
        }

        composable<AgentsRoute> {
            AgentsScreen(
                onNavigateToDetail = { agentId ->
                    navController.navigate(AgentDetailRoute(agentId = agentId))
                },
                onNavigateToAdd = { navController.navigate(AddAgentRoute) },
                edgeMargin = edgeMargin,
            )
        }

        composable<AgentDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AgentDetailRoute>()
            AgentDetailScreen(
                agentId = route.agentId,
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onNavigateToAddConnection = {
                    navController.navigate(ApiKeyDetailRoute(keyId = null))
                },
                edgeMargin = edgeMargin,
            )
        }

        composable<AddAgentRoute> {
            AddAgentScreen(
                onSaved = { navController.popBackStack() },
                onNavigateToAddConnection = {
                    navController.navigate(ApiKeyDetailRoute(keyId = null))
                },
                edgeMargin = edgeMargin,
            )
        }

        composable<PluginsRoute> {
            PluginsScreen(
                onNavigateToDetail = { pluginId ->
                    navController.navigate(PluginDetailRoute(pluginId = pluginId))
                },
                edgeMargin = edgeMargin,
            )
        }

        composable<PluginDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PluginDetailRoute>()
            PluginDetailScreen(
                pluginId = route.pluginId,
                onBack = { navController.popBackStack() },
                edgeMargin = edgeMargin,
            )
        }

        composable<ConsoleRoute> {
            ConsoleScreen(edgeMargin = edgeMargin)
        }

        composable<SettingsRoute> {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                onNavigateToServiceConfig = { navController.navigate(ServiceConfigRoute) },
                onNavigateToBattery = { navController.navigate(BatterySettingsRoute) },
                onNavigateToApiKeys = { navController.navigate(ApiKeysRoute) },
                onNavigateToChannels = { navController.navigate(ConnectedChannelsRoute) },
                onNavigateToLogViewer = { navController.navigate(LogViewerRoute) },
                onNavigateToDoctor = { navController.navigate(DoctorRoute) },
                onNavigateToIdentity = { navController.navigate(IdentityRoute) },
                onNavigateToAbout = { navController.navigate(AboutRoute) },
                onNavigateToUpdates = { navController.navigate(UpdatesRoute) },
                onNavigateToAutonomy = { navController.navigate(AutonomyRoute) },
                onNavigateToTunnel = { navController.navigate(TunnelRoute) },
                onNavigateToGateway = { navController.navigate(GatewayRoute) },
                onNavigateToToolManagement = { navController.navigate(ToolManagementRoute) },
                onNavigateToModelRoutes = { navController.navigate(ModelRoutesRoute) },
                onNavigateToMemoryAdvanced = { navController.navigate(MemoryAdvancedRoute) },
                onNavigateToScheduler = { navController.navigate(SchedulerRoute) },
                onNavigateToObservability = { navController.navigate(ObservabilityRoute) },
                onNavigateToSecurityOverview = { navController.navigate(SecurityOverviewRoute) },
                onRerunWizard = {
                    settingsViewModel.resetOnboarding()
                    navController.navigate(OnboardingRoute) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                edgeMargin = edgeMargin,
            )
        }

        composable<ServiceConfigRoute> {
            ServiceConfigScreen(edgeMargin = edgeMargin)
        }

        composable<IdentityRoute> {
            IdentityScreen(edgeMargin = edgeMargin)
        }

        composable<BatterySettingsRoute> {
            BatterySettingsScreen(edgeMargin = edgeMargin)
        }

        composable<ApiKeysRoute> {
            val context = LocalContext.current
            val apiKeysViewModel: ApiKeysViewModel = viewModel()
            val credentialsLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    uri?.let { apiKeysViewModel.importCredentialsFile(context, it) }
                }
            ApiKeysScreen(
                onNavigateToDetail = { keyId ->
                    navController.navigate(ApiKeyDetailRoute(keyId = keyId))
                },
                onRequestBiometric = { keyId ->
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        val executor = ContextCompat.getMainExecutor(context)
                        val biometricPrompt =
                            BiometricPrompt(
                                activity,
                                executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult,
                                    ) {
                                        apiKeysViewModel.revealKey(keyId)
                                    }
                                },
                            )
                        val promptInfo =
                            BiometricPrompt.PromptInfo
                                .Builder()
                                .setTitle("Reveal API Key")
                                .setSubtitle("Authenticate to view the full key")
                                .setNegativeButtonText("Cancel")
                                .build()
                        biometricPrompt.authenticate(promptInfo)
                    } else {
                        apiKeysViewModel.revealKey(keyId)
                    }
                },
                onExportResult = { payload ->
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, payload)
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "ZeroClaw API Keys (encrypted)",
                            )
                        }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Share encrypted keys",
                        ),
                    )
                },
                onImportCredentials = {
                    credentialsLauncher.launch(arrayOf("application/json", "*/*"))
                },
                edgeMargin = edgeMargin,
                apiKeysViewModel = apiKeysViewModel,
            )
        }

        composable<ApiKeyDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ApiKeyDetailRoute>()
            ApiKeyDetailScreen(
                keyId = route.keyId,
                onSaved = { navController.popBackStack() },
                edgeMargin = edgeMargin,
            )
        }

        composable<ConnectedChannelsRoute> {
            ConnectedChannelsScreen(
                onNavigateToDetail = { channelId, channelType ->
                    navController.navigate(
                        ChannelDetailRoute(
                            channelId = channelId,
                            channelType = channelType,
                        ),
                    )
                },
                edgeMargin = edgeMargin,
            )
        }

        composable<ChannelDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChannelDetailRoute>()
            ChannelDetailScreen(
                channelId = route.channelId,
                channelTypeName = route.channelType,
                onSaved = { navController.popBackStack() },
                edgeMargin = edgeMargin,
            )
        }

        composable<LogViewerRoute> {
            LogViewerScreen(edgeMargin = edgeMargin)
        }

        composable<DoctorRoute> {
            DoctorScreen(
                edgeMargin = edgeMargin,
                onNavigateToRoute = { route ->
                    when {
                        route == "agents" -> navController.navigate(AgentsRoute)
                        route == "api-keys" -> navController.navigate(ApiKeysRoute)
                        route == "battery-settings" -> navController.navigate(BatterySettingsRoute)
                        route.startsWith("agent-detail/") -> {
                            val agentId = route.removePrefix("agent-detail/")
                            navController.navigate(AgentDetailRoute(agentId = agentId))
                        }
                        route.startsWith("api-key-detail/") -> {
                            val keyId = route.removePrefix("api-key-detail/")
                            navController.navigate(ApiKeyDetailRoute(keyId = keyId))
                        }
                    }
                },
            )
        }

        composable<AboutRoute> {
            AboutScreen(edgeMargin = edgeMargin)
        }

        composable<UpdatesRoute> {
            UpdatesScreen(edgeMargin = edgeMargin)
        }

        composable<AutonomyRoute> {
            AutonomyScreen(edgeMargin = edgeMargin)
        }

        composable<SecurityOverviewRoute> {
            SecurityOverviewScreen(edgeMargin = edgeMargin)
        }

        composable<TunnelRoute> {
            TunnelScreen(edgeMargin = edgeMargin)
        }

        composable<GatewayRoute> {
            GatewayScreen(edgeMargin = edgeMargin)
        }

        composable<ToolManagementRoute> {
            ToolManagementScreen(edgeMargin = edgeMargin)
        }

        composable<ModelRoutesRoute> {
            ModelRoutesScreen(edgeMargin = edgeMargin)
        }

        composable<MemoryAdvancedRoute> {
            MemoryAdvancedScreen(edgeMargin = edgeMargin)
        }

        composable<SchedulerRoute> {
            SchedulerScreen(edgeMargin = edgeMargin)
        }

        composable<ObservabilityRoute> {
            ObservabilityScreen(edgeMargin = edgeMargin)
        }

        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}
