// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiToolSpec
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
 * Unit tests for [ToolsBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("ToolsBridge")
class ToolsBridgeTest {
    private lateinit var bridge: ToolsBridge

    /** Sets up mocks and creates a [ToolsBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = ToolsBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("listTools converts FFI records to ToolSpec models")
    fun `listTools converts FFI records to ToolSpec models`() =
        runTest {
            val ffiTools =
                listOf(
                    makeFfiToolSpec(name = "shell", source = "built-in"),
                    makeFfiToolSpec(name = "custom_tool", source = "my-skill"),
                )
            every { com.zeroclaw.ffi.listTools() } returns ffiTools

            val result = bridge.listTools()

            assertEquals(2, result.size)
            assertEquals("shell", result[0].name)
            assertEquals("built-in", result[0].source)
            assertEquals("custom_tool", result[1].name)
            assertEquals("my-skill", result[1].source)
        }

    @Test
    @DisplayName("listTools returns empty list when no tools exist")
    fun `listTools returns empty list when no tools exist`() =
        runTest {
            every { com.zeroclaw.ffi.listTools() } returns emptyList()

            val result = bridge.listTools()

            assertTrue(result.isEmpty())
        }

    @Test
    @DisplayName("listTools propagates FfiException")
    fun `listTools propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.listTools()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.listTools()
            }
        }

    @Test
    @DisplayName("listTools preserves parametersJson field")
    fun `listTools preserves parametersJson field`() =
        runTest {
            val schema = """{"type":"object","properties":{"path":{"type":"string"}}}"""
            val ffiTools =
                listOf(
                    makeFfiToolSpec(name = "file_read", parametersJson = schema),
                )
            every { com.zeroclaw.ffi.listTools() } returns ffiTools

            val result = bridge.listTools()

            assertEquals(schema, result[0].parametersJson)
        }

    /** Helper to construct an [FfiToolSpec] with sensible defaults. */
    companion object {
        private fun makeFfiToolSpec(
            name: String = "test-tool",
            description: String = "A test tool",
            source: String = "built-in",
            parametersJson: String = "{}",
        ): FfiToolSpec =
            FfiToolSpec(
                name = name,
                description = description,
                source = source,
                parametersJson = parametersJson,
            )
    }
}
