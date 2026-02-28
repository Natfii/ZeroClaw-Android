/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.oauth

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OpenAiOAuthManager")
class OpenAiOAuthManagerTest {
    @Test
    @DisplayName("generatePkceState produces valid code verifier length")
    fun `generatePkceState produces valid code verifier length`() {
        val pkce = OpenAiOAuthManager.generatePkceState()
        assertTrue(pkce.codeVerifier.length >= 43) {
            "Code verifier should be at least 43 chars (base64url of 64 bytes), " +
                "but was ${pkce.codeVerifier.length}"
        }
    }

    @Test
    @DisplayName("generatePkceState produces unique state nonces")
    fun `generatePkceState produces unique state nonces`() {
        val first = OpenAiOAuthManager.generatePkceState()
        val second = OpenAiOAuthManager.generatePkceState()
        assertNotEquals(first.state, second.state) {
            "Two consecutive PKCE states should have distinct nonces"
        }
        assertNotEquals(first.codeVerifier, second.codeVerifier) {
            "Two consecutive PKCE states should have distinct code verifiers"
        }
    }

    @Test
    @DisplayName("generatePkceState code challenge is base64url encoded")
    fun `generatePkceState code challenge is base64url encoded`() {
        val pkce = OpenAiOAuthManager.generatePkceState()
        val decoded = Base64.getUrlDecoder().decode(pkce.codeChallenge)
        assertEquals(
            SHA256_DIGEST_LENGTH,
            decoded.size,
        ) {
            "Code challenge should decode to a 32-byte SHA-256 digest"
        }
    }

    @Test
    @DisplayName("buildAuthorizeUrl includes all required parameters")
    fun `buildAuthorizeUrl includes all required parameters`() {
        val pkce =
            PkceState(
                codeVerifier = "test-verifier",
                codeChallenge = "test-challenge",
                state = "test-state",
            )

        val url = OpenAiOAuthManager.buildAuthorizeUrl(pkce)

        assertTrue(url.startsWith("https://auth.openai.com/oauth/authorize?")) {
            "URL should start with the OpenAI authorize endpoint"
        }
        assertTrue("response_type=code" in url) {
            "URL should contain response_type=code"
        }
        assertTrue("client_id=app_EMoamEEZ73f0CkXaXp7hrann" in url) {
            "URL should contain the correct client_id"
        }
        assertTrue("redirect_uri=" in url) {
            "URL should contain redirect_uri"
        }
        assertTrue("scope=" in url) {
            "URL should contain scope"
        }
        assertTrue("code_challenge=test-challenge" in url) {
            "URL should contain the PKCE code challenge"
        }
        assertTrue("code_challenge_method=S256" in url) {
            "URL should contain code_challenge_method=S256"
        }
        assertTrue("state=test-state" in url) {
            "URL should contain the state nonce"
        }
    }

    /** SHA-256 produces a 32-byte digest. */
    companion object {
        private const val SHA256_DIGEST_LENGTH = 32
    }
}
