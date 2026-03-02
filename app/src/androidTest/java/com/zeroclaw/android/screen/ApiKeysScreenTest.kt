/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.data.StorageHealth
import com.zeroclaw.android.screen.helpers.fakeApiKeysState
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeysContent
import com.zeroclaw.android.ui.screen.settings.apikeys.ApiKeysState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose screen tests for [ApiKeysContent].
 */
@RunWith(AndroidJUnit4::class)
class ApiKeysScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun keyList_showsProviderName() {
        composeTestRule.setContent {
            ApiKeysContent(
                state = fakeApiKeysState(),
                snackbarHostState = remember { SnackbarHostState() },
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onRequestBiometric = {},
                onHideRevealedKey = {},
                onDeleteKey = { _, _ -> },
                onCountAgentsForKey = { 0 },
                onRotateKey = { _, _ -> },
                onExportKeys = { _, _ -> },
                onImportKeys = { _, _, _ -> },
                onShowSnackbar = {},
                onExportResult = {},
                onImportCredentials = {},
            )
        }
        composeTestRule
            .onNodeWithText("OpenAI")
            .assertIsDisplayed()
    }

    @Test
    fun fab_isVisible() {
        composeTestRule.setContent {
            ApiKeysContent(
                state = fakeApiKeysState(),
                snackbarHostState = remember { SnackbarHostState() },
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onRequestBiometric = {},
                onHideRevealedKey = {},
                onDeleteKey = { _, _ -> },
                onCountAgentsForKey = { 0 },
                onRotateKey = { _, _ -> },
                onExportKeys = { _, _ -> },
                onImportKeys = { _, _, _ -> },
                onShowSnackbar = {},
                onExportResult = {},
                onImportCredentials = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Add API key")
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoKeysMessage() {
        composeTestRule.setContent {
            ApiKeysContent(
                state =
                    ApiKeysState(
                        keys = emptyList(),
                        revealedKeyId = null,
                        corruptCount = 0,
                        unusedKeyIds = emptySet(),
                        unreachableKeyIds = emptySet(),
                        storageHealth = StorageHealth.Healthy,
                    ),
                snackbarHostState = remember { SnackbarHostState() },
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onRequestBiometric = {},
                onHideRevealedKey = {},
                onDeleteKey = { _, _ -> },
                onCountAgentsForKey = { 0 },
                onRotateKey = { _, _ -> },
                onExportKeys = { _, _ -> },
                onImportKeys = { _, _, _ -> },
                onShowSnackbar = {},
                onExportResult = {},
                onImportCredentials = {},
            )
        }
        composeTestRule
            .onNodeWithText("No API keys stored")
            .assertIsDisplayed()
    }

    @Test
    fun exportImportButtons_areVisible() {
        composeTestRule.setContent {
            ApiKeysContent(
                state = fakeApiKeysState(),
                snackbarHostState = remember { SnackbarHostState() },
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onRequestBiometric = {},
                onHideRevealedKey = {},
                onDeleteKey = { _, _ -> },
                onCountAgentsForKey = { 0 },
                onRotateKey = { _, _ -> },
                onExportKeys = { _, _ -> },
                onImportKeys = { _, _, _ -> },
                onShowSnackbar = {},
                onExportResult = {},
                onImportCredentials = {},
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Export API keys")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Import API keys")
            .assertIsDisplayed()
    }

    @Test
    fun degradedStorage_showsWarning() {
        composeTestRule.setContent {
            ApiKeysContent(
                state =
                    fakeApiKeysState().copy(
                        storageHealth = StorageHealth.Degraded,
                    ),
                snackbarHostState = remember { SnackbarHostState() },
                edgeMargin = 16.dp,
                onNavigateToDetail = {},
                onRequestBiometric = {},
                onHideRevealedKey = {},
                onDeleteKey = { _, _ -> },
                onCountAgentsForKey = { 0 },
                onRotateKey = { _, _ -> },
                onExportKeys = { _, _ -> },
                onImportKeys = { _, _, _ -> },
                onShowSnackbar = {},
                onExportResult = {},
                onImportCredentials = {},
            )
        }
        composeTestRule
            .onNodeWithText("Encrypted storage unavailable", substring = true)
            .assertIsDisplayed()
    }
}
