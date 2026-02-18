// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiSkill
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [SkillsBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("SkillsBridge")
class SkillsBridgeTest {
    private lateinit var bridge: SkillsBridge

    /** Sets up mocks and creates a [SkillsBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = SkillsBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("listSkills converts FFI records to Skill models")
    fun `listSkills converts FFI records to Skill models`() =
        runTest {
            val ffiSkills =
                listOf(
                    makeFfiSkill(name = "skill-a", toolCount = 3u),
                    makeFfiSkill(name = "skill-b", author = null),
                )
            every { com.zeroclaw.ffi.listSkills() } returns ffiSkills

            val result = bridge.listSkills()

            assertEquals(2, result.size)
            assertEquals("skill-a", result[0].name)
            assertEquals(3, result[0].toolCount)
            assertEquals("skill-b", result[1].name)
            assertEquals(null, result[1].author)
        }

    @Test
    @DisplayName("listSkills returns empty list when no skills exist")
    fun `listSkills returns empty list when no skills exist`() =
        runTest {
            every { com.zeroclaw.ffi.listSkills() } returns emptyList()

            val result = bridge.listSkills()

            assertTrue(result.isEmpty())
        }

    @Test
    @DisplayName("listSkills propagates FfiException")
    fun `listSkills propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.listSkills()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.listSkills()
            }
        }

    @Test
    @DisplayName("installSkill delegates to FFI")
    fun `installSkill delegates to FFI`() =
        runTest {
            every { com.zeroclaw.ffi.installSkill("https://example.com/skill") } returns Unit

            bridge.installSkill("https://example.com/skill")

            verify(exactly = 1) {
                com.zeroclaw.ffi.installSkill("https://example.com/skill")
            }
        }

    @Test
    @DisplayName("installSkill propagates FfiException")
    fun `installSkill propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.installSkill(any())
            } throws FfiException.SpawnException("install failed")

            assertThrows<FfiException> {
                bridge.installSkill("bad-source")
            }
        }

    @Test
    @DisplayName("removeSkill delegates to FFI")
    fun `removeSkill delegates to FFI`() =
        runTest {
            every { com.zeroclaw.ffi.removeSkill("test-skill") } returns Unit

            bridge.removeSkill("test-skill")

            verify(exactly = 1) { com.zeroclaw.ffi.removeSkill("test-skill") }
        }

    @Test
    @DisplayName("removeSkill propagates FfiException")
    fun `removeSkill propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.removeSkill(any())
            } throws FfiException.SpawnException("remove failed")

            assertThrows<FfiException> {
                bridge.removeSkill("bad-name")
            }
        }

    /** Helper to construct an [FfiSkill] with sensible defaults. */
    companion object {
        @Suppress("LongParameterList")
        private fun makeFfiSkill(
            name: String = "test-skill",
            description: String = "A test skill",
            version: String = "1.0.0",
            author: String? = "tester",
            tags: List<String> = listOf("test"),
            toolCount: UInt = 0u,
            toolNames: List<String> = emptyList(),
        ): FfiSkill =
            FfiSkill(
                name = name,
                description = description,
                version = version,
                author = author,
                tags = tags,
                toolCount = toolCount,
                toolNames = toolNames,
            )
    }
}
