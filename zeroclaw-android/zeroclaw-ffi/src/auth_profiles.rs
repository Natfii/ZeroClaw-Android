/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Auth profile management for reading and removing OAuth/token profiles.
//!
//! Reads the upstream `auth-profiles.json` file from the daemon's workspace.
//! Profiles are stored by Kotlin's [`AuthProfileWriter`] and consumed by the
//! Rust daemon at startup. This module provides read and delete operations
//! so the Kotlin UI can display and manage stored profiles.

use serde::Deserialize;
use std::collections::BTreeMap;

use crate::error::FfiError;
use crate::runtime;

/// A single auth profile entry exposed to Kotlin via UniFFI.
///
/// Represents either an OAuth token set or a manual API key stored
/// in the daemon's `auth-profiles.json` workspace file.
///
/// @property id Profile ID in `"provider:profile_name"` format.
/// @property provider Provider name (e.g. `"openai-codex"`, `"gemini"`).
/// @property profile_name Human-readable profile display name.
/// @property kind Profile kind: `"oauth"` or `"token"`.
/// @property is_active Whether this is the active profile for its provider.
/// @property expires_at_ms Token expiry as epoch milliseconds, if available.
/// @property created_at_ms Profile creation time as epoch milliseconds.
/// @property updated_at_ms Last update time as epoch milliseconds.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiAuthProfile {
    /// Profile ID in `"provider:profile_name"` format.
    pub id: String,
    /// Provider name (e.g. `"openai-codex"`, `"gemini"`).
    pub provider: String,
    /// Human-readable profile display name.
    pub profile_name: String,
    /// Profile kind: `"oauth"` or `"token"`.
    pub kind: String,
    /// Whether this is the active profile for its provider.
    pub is_active: bool,
    /// Token expiry as epoch milliseconds, if available.
    pub expires_at_ms: Option<i64>,
    /// Profile creation time as epoch milliseconds.
    pub created_at_ms: i64,
    /// Last update time as epoch milliseconds.
    pub updated_at_ms: i64,
}

/// On-disk representation of the `auth-profiles.json` file.
#[derive(Debug, Deserialize)]
struct ProfilesFile {
    /// Map of provider name to the active profile ID for that provider.
    #[serde(default)]
    active_profiles: BTreeMap<String, String>,
    /// Map of profile ID to its full entry.
    #[serde(default)]
    profiles: BTreeMap<String, ProfileEntry>,
}

/// A single profile entry in the on-disk JSON.
#[derive(Debug, Deserialize)]
struct ProfileEntry {
    /// Profile ID (e.g. `"openai-codex:default"`).
    id: String,
    /// Provider name.
    provider: String,
    /// Display name.
    profile_name: String,
    /// `"oauth"` or `"token"`.
    kind: String,
    /// Optional token set containing expiry information.
    #[serde(default)]
    token_set: Option<TokenSetEntry>,
    /// RFC 3339 creation timestamp.
    created_at: String,
    /// RFC 3339 last-updated timestamp.
    updated_at: String,
}

/// Token set metadata within a profile entry.
#[derive(Debug, Deserialize)]
struct TokenSetEntry {
    /// Optional RFC 3339 expiry timestamp.
    #[serde(default)]
    expires_at: Option<String>,
}

/// Lists all auth profiles from the daemon's workspace.
///
/// Reads `auth-profiles.json` from the running daemon's workspace directory,
/// parses all profile entries, and returns them as [`FfiAuthProfile`] records.
/// Returns an empty list if the file does not exist (no profiles stored yet).
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// or [`FfiError::SpawnError`] on I/O or parse failure.
pub(crate) fn list_auth_profiles_inner() -> Result<Vec<FfiAuthProfile>, FfiError> {
    let workspace_dir = runtime::with_daemon_config(|c| c.workspace_dir.clone())?;
    let path = workspace_dir.join("auth-profiles.json");

    if !path.exists() {
        return Ok(vec![]);
    }

    let contents = std::fs::read_to_string(&path).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to read auth-profiles.json: {e}"),
    })?;

    let data: ProfilesFile = serde_json::from_str(&contents).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to parse auth-profiles.json: {e}"),
    })?;

    let profiles = data
        .profiles
        .values()
        .map(|p| {
            let is_active = data
                .active_profiles
                .get(&p.provider)
                .is_some_and(|active_id| active_id == &p.id);

            let expires_at_ms = p
                .token_set
                .as_ref()
                .and_then(|ts| ts.expires_at.as_deref())
                .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
                .map(|dt| dt.timestamp_millis());

            let created_at_ms = chrono::DateTime::parse_from_rfc3339(&p.created_at)
                .map(|dt| dt.timestamp_millis())
                .unwrap_or(0);

            let updated_at_ms = chrono::DateTime::parse_from_rfc3339(&p.updated_at)
                .map(|dt| dt.timestamp_millis())
                .unwrap_or(0);

            FfiAuthProfile {
                id: p.id.clone(),
                provider: p.provider.clone(),
                profile_name: p.profile_name.clone(),
                kind: p.kind.clone(),
                is_active,
                expires_at_ms,
                created_at_ms,
                updated_at_ms,
            }
        })
        .collect();

    Ok(profiles)
}

/// Removes an auth profile identified by provider and profile name.
///
/// Constructs the profile ID as `"provider:profile_name"`, removes it from
/// the `profiles` map, and clears the `active_profiles` entry if the removed
/// profile was active. Writes the updated JSON back to disk.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or the
/// auth-profiles file does not exist,
/// or [`FfiError::SpawnError`] on I/O, parse, or serialisation failure.
pub(crate) fn remove_auth_profile_inner(
    provider: String,
    profile_name: String,
) -> Result<(), FfiError> {
    let workspace_dir = runtime::with_daemon_config(|c| c.workspace_dir.clone())?;
    let path = workspace_dir.join("auth-profiles.json");

    if !path.exists() {
        return Err(FfiError::StateError {
            detail: "auth-profiles.json not found".into(),
        });
    }

    let contents = std::fs::read_to_string(&path).map_err(|e| FfiError::SpawnError {
        detail: format!("read error: {e}"),
    })?;

    let mut data: serde_json::Value =
        serde_json::from_str(&contents).map_err(|e| FfiError::SpawnError {
            detail: format!("parse error: {e}"),
        })?;

    let profile_id = format!("{}:{}", provider.trim(), profile_name.trim());

    if let Some(profiles) = data.get_mut("profiles").and_then(|v| v.as_object_mut()) {
        profiles.remove(&profile_id);
    }

    if let Some(active) = data
        .get_mut("active_profiles")
        .and_then(|v| v.as_object_mut())
        .filter(|active| active.get(&provider).and_then(|v| v.as_str()) == Some(&profile_id))
    {
        active.remove(&provider);
    }

    let json = serde_json::to_string_pretty(&data).map_err(|e| FfiError::SpawnError {
        detail: format!("serialize error: {e}"),
    })?;

    std::fs::write(&path, json).map_err(|e| FfiError::SpawnError {
        detail: format!("write error: {e}"),
    })?;

    Ok(())
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_list_profiles_not_running() {
        let result = list_auth_profiles_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_remove_profile_not_running() {
        let result = remove_auth_profile_inner("openai".into(), "default".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_parse_profiles_file() {
        let json = r#"{
            "schema_version": 1,
            "active_profiles": {"openai-codex": "openai-codex:default"},
            "profiles": {
                "openai-codex:default": {
                    "id": "openai-codex:default",
                    "provider": "openai-codex",
                    "profile_name": "default",
                    "kind": "oauth",
                    "token_set": {
                        "expires_at": "2026-03-01T12:00:00Z"
                    },
                    "created_at": "2026-02-28T10:00:00Z",
                    "updated_at": "2026-02-28T10:00:00Z"
                },
                "gemini:work": {
                    "id": "gemini:work",
                    "provider": "gemini",
                    "profile_name": "work",
                    "kind": "token",
                    "created_at": "2026-02-27T08:00:00Z",
                    "updated_at": "2026-02-27T08:00:00Z"
                }
            }
        }"#;

        let data: ProfilesFile = serde_json::from_str(json).unwrap();
        assert_eq!(data.profiles.len(), 2);
        assert_eq!(data.active_profiles.len(), 1);

        let codex = &data.profiles["openai-codex:default"];
        assert_eq!(codex.provider, "openai-codex");
        assert_eq!(codex.kind, "oauth");
        assert!(codex.token_set.is_some());
        assert_eq!(
            codex.token_set.as_ref().unwrap().expires_at.as_deref(),
            Some("2026-03-01T12:00:00Z")
        );

        let gemini = &data.profiles["gemini:work"];
        assert_eq!(gemini.provider, "gemini");
        assert_eq!(gemini.kind, "token");
        assert!(gemini.token_set.is_none());
    }

    #[test]
    fn test_parse_empty_profiles_file() {
        let json = r#"{"schema_version": 1}"#;
        let data: ProfilesFile = serde_json::from_str(json).unwrap();
        assert!(data.profiles.is_empty());
        assert!(data.active_profiles.is_empty());
    }

    #[test]
    fn test_is_active_detection() {
        let json = r#"{
            "active_profiles": {"provider_a": "provider_a:one"},
            "profiles": {
                "provider_a:one": {
                    "id": "provider_a:one",
                    "provider": "provider_a",
                    "profile_name": "one",
                    "kind": "token",
                    "created_at": "2026-01-01T00:00:00Z",
                    "updated_at": "2026-01-01T00:00:00Z"
                },
                "provider_a:two": {
                    "id": "provider_a:two",
                    "provider": "provider_a",
                    "profile_name": "two",
                    "kind": "token",
                    "created_at": "2026-01-01T00:00:00Z",
                    "updated_at": "2026-01-01T00:00:00Z"
                }
            }
        }"#;

        let data: ProfilesFile = serde_json::from_str(json).unwrap();

        let one = &data.profiles["provider_a:one"];
        let is_active_one = data
            .active_profiles
            .get(&one.provider)
            .is_some_and(|id| id == &one.id);
        assert!(is_active_one);

        let two = &data.profiles["provider_a:two"];
        let is_active_two = data
            .active_profiles
            .get(&two.provider)
            .is_some_and(|id| id == &two.id);
        assert!(!is_active_two);
    }
}
