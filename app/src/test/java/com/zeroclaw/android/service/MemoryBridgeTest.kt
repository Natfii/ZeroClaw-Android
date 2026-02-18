// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiMemoryEntry
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [MemoryBridge].
 *
 * Uses MockK to mock the static UniFFI-generated functions so that tests
 * run without loading the native library.
 */
@DisplayName("MemoryBridge")
class MemoryBridgeTest {
    private lateinit var bridge: MemoryBridge

    /** Sets up mocks and creates a [MemoryBridge] with an unconfined dispatcher. */
    @BeforeEach
    fun setUp() {
        mockkStatic("com.zeroclaw.ffi.Zeroclaw_androidKt")
        bridge = MemoryBridge(ioDispatcher = UnconfinedTestDispatcher())
    }

    /** Tears down all mocks after each test. */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("listMemories converts FFI records to MemoryEntry models")
    fun `listMemories converts FFI records to MemoryEntry models`() =
        runTest {
            val ffiEntries =
                listOf(
                    makeFfiMemoryEntry(id = "id-1", key = "lang", category = "core"),
                    makeFfiMemoryEntry(id = "id-2", key = "task", category = "daily"),
                )
            every {
                com.zeroclaw.ffi.listMemories(null, MemoryBridge.DEFAULT_LIMIT)
            } returns ffiEntries

            val result = bridge.listMemories()

            assertEquals(2, result.size)
            assertEquals("id-1", result[0].id)
            assertEquals("core", result[0].category)
            assertEquals("id-2", result[1].id)
            assertEquals("daily", result[1].category)
        }

    @Test
    @DisplayName("listMemories with category filter passes category to FFI")
    fun `listMemories with category filter passes category to FFI`() =
        runTest {
            every {
                com.zeroclaw.ffi.listMemories("core", MemoryBridge.DEFAULT_LIMIT)
            } returns emptyList()

            val result = bridge.listMemories(category = "core")

            assertTrue(result.isEmpty())
            verify(exactly = 1) {
                com.zeroclaw.ffi.listMemories("core", MemoryBridge.DEFAULT_LIMIT)
            }
        }

    @Test
    @DisplayName("listMemories propagates FfiException")
    fun `listMemories propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.listMemories(any(), any())
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.listMemories()
            }
        }

    @Test
    @DisplayName("recallMemory converts FFI records to MemoryEntry models")
    fun `recallMemory converts FFI records to MemoryEntry models`() =
        runTest {
            val ffiEntries =
                listOf(
                    makeFfiMemoryEntry(
                        id = "id-1",
                        key = "lang",
                        content = "Rust is great",
                        score = 0.95,
                    ),
                )
            every {
                com.zeroclaw.ffi.recallMemory("Rust", MemoryBridge.DEFAULT_LIMIT)
            } returns ffiEntries

            val result = bridge.recallMemory("Rust")

            assertEquals(1, result.size)
            assertEquals("Rust is great", result[0].content)
            assertEquals(0.95, result[0].score)
        }

    @Test
    @DisplayName("recallMemory propagates FfiException")
    fun `recallMemory propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.recallMemory(any(), any())
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.recallMemory("test")
            }
        }

    @Test
    @DisplayName("forgetMemory returns true when entry deleted")
    fun `forgetMemory returns true when entry deleted`() =
        runTest {
            every { com.zeroclaw.ffi.forgetMemory("test-key") } returns true

            val result = bridge.forgetMemory("test-key")

            assertTrue(result)
        }

    @Test
    @DisplayName("forgetMemory returns false when entry not found")
    fun `forgetMemory returns false when entry not found`() =
        runTest {
            every { com.zeroclaw.ffi.forgetMemory("nonexistent") } returns false

            val result = bridge.forgetMemory("nonexistent")

            assertFalse(result)
        }

    @Test
    @DisplayName("forgetMemory propagates FfiException")
    fun `forgetMemory propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.forgetMemory(any())
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.forgetMemory("key")
            }
        }

    @Test
    @DisplayName("memoryCount delegates to FFI and returns count")
    fun `memoryCount delegates to FFI and returns count`() =
        runTest {
            every { com.zeroclaw.ffi.memoryCount() } returns 42u

            val result = bridge.memoryCount()

            assertEquals(42u, result)
        }

    @Test
    @DisplayName("memoryCount propagates FfiException")
    fun `memoryCount propagates FfiException`() =
        runTest {
            every {
                com.zeroclaw.ffi.memoryCount()
            } throws FfiException.StateException("daemon not running")

            assertThrows<FfiException> {
                bridge.memoryCount()
            }
        }

    /** Helper to construct an [FfiMemoryEntry] with sensible defaults. */
    companion object {
        @Suppress("LongParameterList")
        private fun makeFfiMemoryEntry(
            id: String = "test-id",
            key: String = "test-key",
            content: String = "test content",
            category: String = "core",
            timestamp: String = "2026-02-18T12:00:00Z",
            score: Double? = null,
        ): FfiMemoryEntry =
            FfiMemoryEntry(
                id = id,
                key = key,
                content = content,
                category = category,
                timestamp = timestamp,
                score = score,
            )
    }
}
