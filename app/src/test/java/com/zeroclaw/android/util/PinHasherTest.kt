/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PinHasher].
 *
 * Verifies PBKDF2 hash/verify round-trips, wrong PIN rejection,
 * and salt uniqueness between invocations.
 */
@DisplayName("PinHasher")
class PinHasherTest {
    @Test
    @DisplayName("hash and verify round-trip succeeds")
    fun `hash and verify round-trip succeeds`() {
        val pin = "1234"
        val hash = PinHasher.hash(pin)
        assertTrue(PinHasher.verify(pin, hash))
    }

    @Test
    @DisplayName("wrong PIN is rejected")
    fun `wrong PIN is rejected`() {
        val hash = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("5678", hash))
    }

    @Test
    @DisplayName("different PINs produce different hashes")
    fun `different PINs produce different hashes`() {
        val hash1 = PinHasher.hash("1234")
        val hash2 = PinHasher.hash("5678")
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("same PIN with different salts produces different hashes")
    fun `same PIN with different salts produces different hashes`() {
        val hash1 = PinHasher.hash("1234")
        val hash2 = PinHasher.hash("1234")
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("hash output is non-empty Base64 string")
    fun `hash output is non-empty Base64 string`() {
        val hash = PinHasher.hash("0000")
        assertTrue(hash.isNotEmpty())
        val expectedLength = 48
        val decoded =
            java.util.Base64
                .getDecoder()
                .decode(hash)
        assertEquals(expectedLength, decoded.size)
    }

    @Test
    @DisplayName("verify rejects corrupted hash")
    fun `verify rejects corrupted hash`() {
        val hash = PinHasher.hash("1234")
        val corrupted = hash.dropLast(1) + if (hash.last() == 'A') "B" else "A"
        assertFalse(PinHasher.verify("1234", corrupted))
    }

    @Test
    @DisplayName("verify rejects truncated hash")
    fun `verify rejects truncated hash`() {
        assertFalse(PinHasher.verify("1234", "dG9vc2hvcnQ="))
    }
}
