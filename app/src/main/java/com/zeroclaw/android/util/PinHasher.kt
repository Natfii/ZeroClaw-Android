/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-based PIN hashing utility.
 *
 * Uses PBKDF2WithHmacSHA256 with 600,000 iterations and a 16-byte random salt.
 * The output is a Base64-encoded concatenation of `salt || hash` so that a
 * single string can be stored and later verified.
 *
 * This is a pure JDK implementation with no additional dependencies.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    /**
     * Hashes a PIN with a random salt.
     *
     * @param pin The raw PIN string (4-6 digits).
     * @return Base64-encoded `salt || hash` string suitable for storage.
     */
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val derived = derive(pin, salt)
        return Base64.getEncoder().encodeToString(salt + derived)
    }

    /**
     * Verifies a PIN against a stored hash.
     *
     * Extracts the salt from the stored hash, re-derives the key, and
     * performs a constant-time comparison to prevent timing attacks.
     *
     * @param pin The raw PIN string to verify.
     * @param storedHash Base64-encoded `salt || hash` from [hash].
     * @return True if the PIN matches the stored hash.
     */
    fun verify(
        pin: String,
        storedHash: String,
    ): Boolean {
        val decoded = Base64.getDecoder().decode(storedHash)
        if (decoded.size != SALT_BYTES + HASH_BYTES) return false
        val salt = decoded.copyOfRange(0, SALT_BYTES)
        val expected = decoded.copyOfRange(SALT_BYTES, decoded.size)
        val actual = derive(pin, salt)
        return constantTimeEquals(expected, actual)
    }

    private fun derive(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BYTES * Byte.SIZE_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    @Suppress("ReturnCount")
    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
