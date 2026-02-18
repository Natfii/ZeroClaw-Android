/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.CheckStatus
import com.zeroclaw.android.model.DiagnosticCategory
import com.zeroclaw.android.model.KeyStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DoctorValidator].
 *
 * Mocks all repositories, FFI calls, and Android system services so that
 * tests run without a device or emulator.
 */
@DisplayName("DoctorValidator")
class DoctorValidatorTest {
    private val context: Context = mockk(relaxed = true)
    private val agentRepository: AgentRepository = mockk()
    private val apiKeyRepository: ApiKeyRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var validator: DoctorValidator

    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")

        val pm = mockk<PowerManager>()
        every { pm.isIgnoringBatteryOptimizations(any()) } returns true
        every { context.getSystemService(Context.POWER_SERVICE) } returns pm
        every { context.packageName } returns "com.zeroclaw.android"

        val cm = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns capabilities
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm

        validator =
            DoctorValidator(
                context = context,
                agentRepository = agentRepository,
                apiKeyRepository = apiKeyRepository,
                ioDispatcher = testDispatcher,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("Config checks")
    inner class ConfigChecks {
        @Test
        @DisplayName("passes when agents have unique names and valid config")
        fun `unique names pass`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(
                            testAgent("1", "Agent-A"),
                            testAgent("2", "Agent-B"),
                        ),
                    )
                every { com.zeroclaw.ffi.validateConfig(any()) } returns ""

                val checks = validator.runConfigChecks()

                val nameCheck = checks.first { it.id == "config-duplicate-names" }
                assertEquals(CheckStatus.PASS, nameCheck.status)
                assertTrue(checks.all { it.category == DiagnosticCategory.CONFIG })
            }

        @Test
        @DisplayName("fails when duplicate agent names exist")
        fun `duplicate names fail`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(
                            testAgent("1", "SameName"),
                            testAgent("2", "SameName"),
                        ),
                    )
                every { com.zeroclaw.ffi.validateConfig(any()) } returns ""

                val checks = validator.runConfigChecks()

                val nameCheck = checks.first { it.id == "config-duplicate-names" }
                assertEquals(CheckStatus.FAIL, nameCheck.status)
                assertTrue(nameCheck.detail.contains("SameName"))
            }

        @Test
        @DisplayName("fails when FFI reports parse error")
        fun `invalid config fails`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(testAgent("1", "Agent-A")),
                    )
                every { com.zeroclaw.ffi.validateConfig(any()) } returns "expected value, found EOF"

                val checks = validator.runConfigChecks()

                val agentCheck = checks.first { it.id == "config-agent-1" }
                assertEquals(CheckStatus.FAIL, agentCheck.status)
            }
    }

    @Nested
    @DisplayName("API key checks")
    inner class ApiKeyChecks {
        @Test
        @DisplayName("passes when key exists and is active")
        fun `active key passes`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(testAgent("1", "Agent-A", provider = "openai")),
                    )
                every { apiKeyRepository.keys } returns
                    flowOf(
                        listOf(ApiKey(id = "k1", provider = "openai", key = "sk-test")),
                    )

                val checks = validator.runApiKeyChecks()

                val keyCheck = checks.first { it.id == "apikey-1" }
                assertEquals(CheckStatus.PASS, keyCheck.status)
            }

        @Test
        @DisplayName("fails when no key exists for provider")
        fun `missing key fails`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(testAgent("1", "Agent-A", provider = "openai")),
                    )
                every { apiKeyRepository.keys } returns flowOf(emptyList())

                val checks = validator.runApiKeyChecks()

                val keyCheck = checks.first { it.id == "apikey-1" }
                assertEquals(CheckStatus.FAIL, keyCheck.status)
            }

        @Test
        @DisplayName("fails when key is marked invalid")
        fun `invalid key fails`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(testAgent("1", "Agent-A", provider = "openai")),
                    )
                every { apiKeyRepository.keys } returns
                    flowOf(
                        listOf(
                            ApiKey(
                                id = "k1",
                                provider = "openai",
                                key = "sk-test",
                                status = KeyStatus.INVALID,
                            ),
                        ),
                    )

                val checks = validator.runApiKeyChecks()

                val keyCheck = checks.first { it.id == "apikey-1" }
                assertEquals(CheckStatus.FAIL, keyCheck.status)
            }

        @Test
        @DisplayName("warns when no agents are enabled")
        fun `no enabled agents warns`() =
            runTest {
                every { agentRepository.agents } returns
                    flowOf(
                        listOf(testAgent("1", "Agent-A", enabled = false)),
                    )
                every { apiKeyRepository.keys } returns flowOf(emptyList())

                val checks = validator.runApiKeyChecks()

                val check = checks.first { it.id == "apikey-none" }
                assertEquals(CheckStatus.WARN, check.status)
            }
    }

    @Nested
    @DisplayName("Connectivity checks")
    inner class ConnectivityChecks {
        @Test
        @DisplayName("passes when network is available")
        fun `network available passes`() =
            runTest {
                val checks = validator.runConnectivityChecks()

                val check = checks.first { it.id == "connectivity-network" }
                assertEquals(CheckStatus.PASS, check.status)
            }

        @Test
        @DisplayName("fails when no network")
        fun `no network fails`() =
            runTest {
                val cm = mockk<ConnectivityManager>()
                every { cm.activeNetwork } returns null
                every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm

                val checks = validator.runConnectivityChecks()

                val check = checks.first { it.id == "connectivity-network" }
                assertEquals(CheckStatus.FAIL, check.status)
            }
    }

    @Nested
    @DisplayName("Daemon health checks")
    inner class DaemonHealthChecks {
        @Test
        @DisplayName("passes when daemon is running")
        fun `running daemon passes`() =
            runTest {
                every {
                    com.zeroclaw.ffi.getStatus()
                } returns """{"daemon_running":true,"uptime_seconds":120,"components":{}}"""

                val checks = validator.runDaemonHealthChecks()

                val runningCheck = checks.first { it.id == "daemon-running" }
                assertEquals(CheckStatus.PASS, runningCheck.status)
            }

        @Test
        @DisplayName("warns when daemon is not running")
        fun `stopped daemon warns`() =
            runTest {
                every {
                    com.zeroclaw.ffi.getStatus()
                } returns """{"daemon_running":false}"""

                val checks = validator.runDaemonHealthChecks()

                val runningCheck = checks.first { it.id == "daemon-running" }
                assertEquals(CheckStatus.WARN, runningCheck.status)
            }

        @Test
        @DisplayName("reports component status")
        fun `component statuses reported`() =
            runTest {
                every {
                    com.zeroclaw.ffi.getStatus()
                } returns """{"daemon_running":true,"uptime_seconds":60,"components":{"gateway":{"status":"ok"},"channels":{"status":"error"}}}"""

                val checks = validator.runDaemonHealthChecks()

                val gateway = checks.first { it.id == "daemon-component-gateway" }
                assertEquals(CheckStatus.PASS, gateway.status)

                val channels = checks.first { it.id == "daemon-component-channels" }
                assertEquals(CheckStatus.FAIL, channels.status)
            }
    }

    @Nested
    @DisplayName("System checks")
    inner class SystemChecks {
        @Test
        @DisplayName("passes when battery optimization is exempt")
        fun `battery exempt passes`() {
            mockkObject(com.zeroclaw.android.util.BatteryOptimization)
            every {
                com.zeroclaw.android.util.BatteryOptimization
                    .isExempt(any())
            } returns true
            every {
                com.zeroclaw.android.util.BatteryOptimization
                    .detectAggressiveOem()
            } returns null

            val checks = validator.runSystemChecks()

            val batteryCheck = checks.first { it.id == "system-battery-exempt" }
            assertEquals(CheckStatus.PASS, batteryCheck.status)
        }

        @Test
        @DisplayName("warns when battery optimization not exempt")
        fun `battery not exempt warns`() {
            mockkObject(com.zeroclaw.android.util.BatteryOptimization)
            every {
                com.zeroclaw.android.util.BatteryOptimization
                    .isExempt(any())
            } returns false
            every {
                com.zeroclaw.android.util.BatteryOptimization
                    .detectAggressiveOem()
            } returns null

            val checks = validator.runSystemChecks()

            val batteryCheck = checks.first { it.id == "system-battery-exempt" }
            assertEquals(CheckStatus.WARN, batteryCheck.status)
        }
    }

    @Nested
    @DisplayName("Uptime formatting")
    inner class UptimeFormatting {
        @Test
        @DisplayName("formats seconds only")
        fun `seconds only`() {
            assertEquals("45s", DoctorValidator.formatUptime(45))
        }

        @Test
        @DisplayName("formats minutes and seconds")
        fun `minutes and seconds`() {
            assertEquals("2m 30s", DoctorValidator.formatUptime(150))
        }

        @Test
        @DisplayName("formats hours, minutes, and seconds")
        fun `hours minutes seconds`() {
            assertEquals("1h 30m 0s", DoctorValidator.formatUptime(5400))
        }
    }

    private fun testAgent(
        id: String,
        name: String,
        provider: String = "openai",
        enabled: Boolean = true,
    ): Agent =
        Agent(
            id = id,
            name = name,
            provider = provider,
            modelName = "gpt-4o",
            isEnabled = enabled,
        )
}
