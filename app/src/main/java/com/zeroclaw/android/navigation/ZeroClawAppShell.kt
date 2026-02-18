/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.ui.component.StatusDot
import com.zeroclaw.android.viewmodel.DaemonViewModel

/** Set of top-level routes where the bottom navigation bar should be visible. */
private val topLevelRoutes = TopLevelDestination.entries.map { it.route::class }

/**
 * Root composable providing the application shell with adaptive navigation
 * and a top app bar.
 *
 * Uses [NavigationSuiteScaffold] to automatically switch between a bottom
 * navigation bar (< 600dp), navigation rail (600-840dp), and navigation
 * drawer (840dp+) based on the current window width.
 *
 * The [StatusDot] is visible in the top bar on all screens to provide
 * persistent daemon status feedback.
 *
 * @param windowWidthSizeClass Current [WindowWidthSizeClass] for responsive layout.
 * @param viewModel The [DaemonViewModel] for daemon state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroClawAppShell(
    windowWidthSizeClass: WindowWidthSizeClass,
    viewModel: DaemonViewModel = viewModel(),
) {
    val context = LocalContext.current
    val onboardingRepo = (context.applicationContext as ZeroClawApplication).onboardingRepository
    val onboardingCompleted by onboardingRepo.isCompleted.collectAsStateWithLifecycle(
        initialValue = true,
    )

    val startDestination: Any =
        if (onboardingCompleted) DashboardRoute else OnboardingRoute

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()

    val isOnboarding = currentDestination?.hasRoute(OnboardingRoute::class) == true

    val isTopLevel =
        !isOnboarding &&
            currentDestination?.hierarchy?.any { dest ->
                topLevelRoutes.any { routeClass -> dest.hasRoute(routeClass) }
            } == true

    val edgeMargin =
        if (windowWidthSizeClass == WindowWidthSizeClass.Compact) 16.dp else 24.dp

    if (isOnboarding) {
        Scaffold { innerPadding ->
            ZeroClawNavHost(
                navController = navController,
                startDestination = startDestination,
                edgeMargin = edgeMargin,
                modifier = Modifier.padding(innerPadding),
            )
        }
    } else if (isTopLevel) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { dest ->
                            dest.hasRoute(destination.route::class)
                        } == true
                    item(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector =
                                    if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            TopBarTitle(serviceState = serviceState)
                        },
                    )
                },
            ) { innerPadding ->
                ZeroClawNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    edgeMargin = edgeMargin,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TopBarTitle(serviceState = serviceState)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            ZeroClawNavHost(
                navController = navController,
                startDestination = startDestination,
                edgeMargin = edgeMargin,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * Top app bar title row with app name and daemon [StatusDot].
 *
 * @param serviceState Current [ServiceState] shown in the status dot.
 */
@Composable
private fun TopBarTitle(serviceState: ServiceState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ZeroClaw")
        Spacer(modifier = Modifier.width(8.dp))
        StatusDot(state = serviceState)
    }
}
