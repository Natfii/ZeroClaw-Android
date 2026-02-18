/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility for encrypting and decrypting API key export payloads.
 *
 * Uses PBKDF2WithHmacSHA256 for key derivation (NIST SP 800-132 compliant)
 * and AES-256-GCM for authenticated encryption. The output format is a
 * single Base64-encoded blob: `salt(16) || iv(12) || ciphertext || tag(16)`.
 *
 * GCM mode provides both confidentiality and integrity, so any tampering
 * with the ciphertext or metadata is detected during decryption.
 */
internal object KeyExportCrypto {
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 600_000
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val KEY_ALGORITHM = "AES"

    /**
     * Minimum number of bytes in a valid encrypted payload after Base64 decoding.
     *
     * A valid payload must contain at least the salt, IV, and one byte of
     * ciphertext (which includes the 16-byte GCM authentication tag).
     */
    private const val MIN_PAYLOAD_BYTES = SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 1

    /**
     * Encrypts [plaintext] using a key derived from [passphrase].
     *
     * Generates a random 16-byte salt and 12-byte IV for each invocation,
     * ensuring that encrypting the same plaintext with the same passphrase
     * produces different ciphertext every time.
     *
     * @param plaintext The data to encrypt (typically a JSON string).
     * @param passphrase User-provided encryption passphrase.
     * @return Base64-encoded encrypted payload containing salt, IV, and ciphertext.
     * @throws GeneralSecurityException if the platform does not support the
     *   required cryptographic algorithms.
     */
    fun encrypt(
        plaintext: String,
        passphrase: String,
    ): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(IV_LENGTH_BYTES)
        val random = SecureRandom()
        random.nextBytes(salt)
        random.nextBytes(iv)

        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(SALT_LENGTH_BYTES + IV_LENGTH_BYTES + ciphertext.size)
        System.arraycopy(salt, 0, payload, 0, SALT_LENGTH_BYTES)
        System.arraycopy(iv, 0, payload, SALT_LENGTH_BYTES, IV_LENGTH_BYTES)
        System.arraycopy(
            ciphertext,
            0,
            payload,
            SALT_LENGTH_BYTES + IV_LENGTH_BYTES,
            ciphertext.size,
        )

        return Base64.getEncoder().encodeToString(payload)
    }

    /**
     * Decrypts a payload previously produced by [encrypt].
     *
     * Extracts the salt and IV from the payload header, re-derives the
     * encryption key from [passphrase], and decrypts the ciphertext.
     * GCM authentication ensures that any tampering is detected.
     *
     * @param encryptedPayload Base64-encoded encrypted data from [encrypt].
     * @param passphrase The passphrase used during encryption.
     * @return The original plaintext string.
     * @throws IllegalArgumentException if the payload is too short or
     *   not valid Base64.
     * @throws GeneralSecurityException if decryption fails, which
     *   typically indicates a wrong passphrase or tampered data.
     */
    fun decrypt(
        encryptedPayload: String,
        passphrase: String,
    ): String {
        val payload = Base64.getDecoder().decode(encryptedPayload)
        require(payload.size >= MIN_PAYLOAD_BYTES) {
            "Encrypted payload is too short to contain valid data"
        }

        val salt = payload.copyOfRange(0, SALT_LENGTH_BYTES)
        val iv = payload.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(SALT_LENGTH_BYTES + IV_LENGTH_BYTES, payload.size)

        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val plainBytes = cipher.doFinal(ciphertext)

        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Derives an AES-256 key from [passphrase] and [salt] using PBKDF2.
     *
     * @param passphrase User-provided passphrase.
     * @param salt Random salt bytes.
     * @return An AES [SecretKeySpec] suitable for encryption or decryption.
     */
    private fun deriveKey(
        passphrase: String,
        salt: ByteArray,
    ): SecretKeySpec {
        val keySpec =
            PBEKeySpec(
                passphrase.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS,
            )
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }
}
