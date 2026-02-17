/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.viewmodel

import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.DaemonServiceBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DaemonViewModel].
 *
 * Mocks the [ZeroClawApplication] and [DaemonServiceBridge] to verify
 * that the ViewModel correctly delegates to the bridge and exposes
 * observable state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DaemonViewModel")
class DaemonViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: ZeroClawApplication
    private lateinit var mockBridge: DaemonServiceBridge
    private lateinit var viewModel: DaemonViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        mockBridge =
            mockk(relaxed = true) {
                every { serviceState } returns MutableStateFlow(ServiceState.STOPPED)
                every { lastStatus } returns MutableStateFlow(null)
                every { lastError } returns MutableStateFlow(null)
            }
        mockApp =
            mockk<ZeroClawApplication>(relaxed = true) {
                every { daemonBridge } returns mockBridge
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    @DisplayName("initial status state is Idle")
    fun `initial status state is Idle`() {
        viewModel = DaemonViewModel(mockApp)
        assertEquals(DaemonUiState.Idle, viewModel.statusState.value)
    }

    @Test
    @DisplayName("Error state can hold a retry lambda")
    fun `Error state can hold a retry lambda`() {
        var called = false
        val error = DaemonUiState.Error("test error") { called = true }
        assertNotNull(error.retry)
        error.retry?.invoke()
        assertTrue(called)
    }

    @Test
    @DisplayName("Error state retry is null by default")
    fun `Error state retry is null by default`() {
        val error = DaemonUiState.Error("test error")
        assertNull(error.retry)
    }

    @Test
    @DisplayName("ERROR state propagates lastError detail to statusState")
    fun `ERROR state propagates lastError detail to statusState`() {
        val stateFlow = MutableStateFlow(ServiceState.STOPPED)
        val errorFlow = MutableStateFlow<String?>("config parse error at line 5")
        mockBridge =
            mockk(relaxed = true) {
                every { serviceState } returns stateFlow
                every { lastStatus } returns MutableStateFlow(null)
                every { lastError } returns errorFlow
            }
        mockApp =
            mockk<ZeroClawApplication>(relaxed = true) {
                every { daemonBridge } returns mockBridge
            }
        viewModel = DaemonViewModel(mockApp)

        stateFlow.value = ServiceState.ERROR

        val state = viewModel.statusState.value
        assertTrue(state is DaemonUiState.Error)
        assertEquals("config parse error at line 5", (state as DaemonUiState.Error).detail)
    }

    @Test
    @DisplayName("ERROR state uses fallback when lastError is null")
    fun `ERROR state uses fallback when lastError is null`() {
        val stateFlow = MutableStateFlow(ServiceState.STOPPED)
        val errorFlow = MutableStateFlow<String?>(null)
        mockBridge =
            mockk(relaxed = true) {
                every { serviceState } returns stateFlow
                every { lastStatus } returns MutableStateFlow(null)
                every { lastError } returns errorFlow
            }
        mockApp =
            mockk<ZeroClawApplication>(relaxed = true) {
                every { daemonBridge } returns mockBridge
            }
        viewModel = DaemonViewModel(mockApp)

        stateFlow.value = ServiceState.ERROR

        val state = viewModel.statusState.value
        assertTrue(state is DaemonUiState.Error)
        assertEquals("Unknown daemon error", (state as DaemonUiState.Error).detail)
    }
}
