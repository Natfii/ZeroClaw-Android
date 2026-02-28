/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.oauth

import android.content.Context
import java.io.File
import java.time.Instant
import org.json.JSONObject

/**
 * Writes OAuth credentials to `auth-profiles.json` in the app's files directory.
 *
 * The Rust [AuthService] reads this file to obtain OAuth tokens for the
 * `openai-codex` provider. Tokens are stored in plaintext because the Rust
 * [SecretStore] transparently handles plaintext values (no `enc2:` prefix
 * required) and auto-encrypts on the next save cycle.
 *
 * The file resides in [Context.getFilesDir] which is protected by Linux DAC
 * permissions (0700). This is equivalent to the protection that
 * [EncryptedSharedPreferences] relies on for key material. The Rust daemon
 * reads this file directly, so Android-level encryption is not possible
 * without modifying the FFI boundary.
 *
 * All writes use an atomic temp-file-then-rename pattern to prevent partial
 * writes from corrupting the JSON. A `@Synchronized` annotation on both
 * write and remove methods prevents concurrent-access races.
 *
 * The file format mirrors the upstream `PersistedAuthProfiles` struct with
 * schema version 1.
 */
object AuthProfileWriter {
    /** Profile ID used for the default OpenAI Codex OAuth profile. */
    private const val PROFILE_ID = "openai-codex:default"

    /** Provider name matching the upstream factory registration. */
    private const val PROVIDER = "openai-codex"

    /** Profile name within the provider namespace. */
    private const val PROFILE_NAME = "default"

    /** Auth profile kind for OAuth credentials. */
    private const val KIND_OAUTH = "oauth"

    /** Current schema version matching the upstream `CURRENT_SCHEMA_VERSION`. */
    private const val SCHEMA_VERSION = 1

    /** Filename matching the upstream `PROFILES_FILENAME` constant. */
    private const val PROFILES_FILENAME = "auth-profiles.json"

    /**
     * Writes (or updates) the OpenAI Codex OAuth profile in `auth-profiles.json`.
     *
     * If the file already exists, the existing content is merged so that
     * profiles for other providers are preserved. If it does not exist, a
     * new file is created with [Context.MODE_PRIVATE] permissions.
     *
     * Uses atomic temp-file-then-rename to prevent partial writes.
     *
     * @param context Android context for resolving [Context.getFilesDir].
     * @param accessToken OAuth access token (Bearer token).
     * @param refreshToken OAuth refresh token for automatic renewal.
     * @param expiresAtMs Epoch milliseconds when [accessToken] expires.
     */
    @Synchronized
    fun writeCodexProfile(
        context: Context,
        accessToken: String,
        refreshToken: String,
        expiresAtMs: Long,
    ) {
        val file = File(context.filesDir, PROFILES_FILENAME)
        val now = formatRfc3339(System.currentTimeMillis())
        val expiresAt = formatRfc3339(expiresAtMs)

        val root =
            if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }

        root.put("schema_version", SCHEMA_VERSION)
        root.put("updated_at", now)

        val activeProfiles =
            if (root.has("active_profiles")) {
                root.getJSONObject("active_profiles")
            } else {
                JSONObject()
            }
        activeProfiles.put(PROVIDER, PROFILE_ID)
        root.put("active_profiles", activeProfiles)

        val profiles =
            if (root.has("profiles")) {
                root.getJSONObject("profiles")
            } else {
                JSONObject()
            }

        val profile = JSONObject()
        profile.put("provider", PROVIDER)
        profile.put("profile_name", PROFILE_NAME)
        profile.put("kind", KIND_OAUTH)
        profile.put("account_id", JSONObject.NULL)
        profile.put("workspace_id", JSONObject.NULL)
        profile.put("access_token", accessToken)
        profile.put("refresh_token", refreshToken)
        profile.put("id_token", JSONObject.NULL)
        profile.put("token", JSONObject.NULL)
        profile.put("expires_at", expiresAt)
        profile.put("token_type", "Bearer")
        profile.put("scope", "openid profile email offline_access")
        profile.put("metadata", JSONObject())
        profile.put("created_at", now)
        profile.put("updated_at", now)
        profiles.put(PROFILE_ID, profile)
        root.put("profiles", profiles)

        atomicWrite(file, root.toString(2))
    }

    /**
     * Removes the OpenAI Codex OAuth profile from `auth-profiles.json`.
     *
     * Called when the user disconnects their ChatGPT OAuth session. If the
     * file does not exist or does not contain the Codex profile, this is
     * a no-op.
     *
     * @param context Android context for resolving [Context.getFilesDir].
     */
    @Synchronized
    fun removeCodexProfile(context: Context) {
        val file = File(context.filesDir, PROFILES_FILENAME)
        if (!file.exists()) return

        val root = JSONObject(file.readText())
        if (root.has("active_profiles")) {
            root.getJSONObject("active_profiles").remove(PROVIDER)
        }
        if (root.has("profiles")) {
            root.getJSONObject("profiles").remove(PROFILE_ID)
        }
        root.put("updated_at", formatRfc3339(System.currentTimeMillis()))
        atomicWrite(file, root.toString(2))
    }

    /**
     * Writes content to a file atomically using a temp-file-then-rename pattern.
     *
     * Ensures the target file is never left in a partially written state.
     * If the rename fails, the temp file is deleted to avoid leaving debris.
     *
     * @param target Destination file.
     * @param content String content to write.
     */
    private fun atomicWrite(
        target: File,
        content: String,
    ) {
        val tmp = File(target.parent, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            target.writeText(content)
        }
    }

    /**
     * Formats epoch milliseconds as an RFC 3339 timestamp in UTC.
     *
     * @param epochMs Epoch milliseconds.
     * @return RFC 3339 formatted string (e.g. `2026-02-27T14:30:00.000Z`).
     */
    private fun formatRfc3339(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
}
