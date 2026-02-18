/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Memory browsing and management for the Android dashboard.
//!
//! Provides read-only access to the daemon's memory backend for listing,
//! searching, and counting memory entries. Also supports deleting entries
//! via the `forget` operation.

use crate::error::FfiError;

/// A memory entry suitable for transfer across the FFI boundary.
///
/// Maps to the upstream [`zeroclaw::memory::MemoryEntry`] but represents
/// the category as a plain string for FFI simplicity.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiMemoryEntry {
    /// Unique identifier of this memory entry.
    pub id: String,
    /// Key under which the memory is stored.
    pub key: String,
    /// Content text of the memory entry.
    pub content: String,
    /// Category string: `"core"`, `"daily"`, `"conversation"`, or a custom name.
    pub category: String,
    /// RFC 3339 timestamp of when the entry was created.
    pub timestamp: String,
    /// Relevance score from a recall query, if applicable.
    pub score: Option<f64>,
}

/// Converts an upstream [`zeroclaw::memory::MemoryEntry`] to an [`FfiMemoryEntry`].
fn to_ffi(entry: &zeroclaw::memory::MemoryEntry) -> FfiMemoryEntry {
    FfiMemoryEntry {
        id: entry.id.clone(),
        key: entry.key.clone(),
        content: entry.content.clone(),
        category: entry.category.to_string(),
        timestamp: entry.timestamp.clone(),
        score: entry.score,
    }
}

/// Parses a category string into the upstream [`MemoryCategory`] enum.
fn parse_category(cat: &str) -> zeroclaw::memory::MemoryCategory {
    match cat {
        "core" => zeroclaw::memory::MemoryCategory::Core,
        "daily" => zeroclaw::memory::MemoryCategory::Daily,
        "conversation" => zeroclaw::memory::MemoryCategory::Conversation,
        other => zeroclaw::memory::MemoryCategory::Custom(other.to_string()),
    }
}

/// Lists memory entries, optionally filtered by category.
///
/// When `category` is `None`, returns all entries. When provided, only
/// entries matching the category are returned. Results are truncated to
/// `limit` entries.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// the memory backend is not available, or [`FfiError::SpawnError`]
/// on backend access failure.
pub(crate) fn list_memories_inner(
    category: Option<String>,
    limit: u32,
) -> Result<Vec<FfiMemoryEntry>, FfiError> {
    crate::runtime::with_memory(|memory, handle| {
        let cat = category.as_deref().map(parse_category);
        let entries =
            handle
                .block_on(memory.list(cat.as_ref()))
                .map_err(|e| FfiError::SpawnError {
                    detail: format!("memory list failed: {e}"),
                })?;
        let limit = limit as usize;
        Ok(entries.iter().take(limit).map(to_ffi).collect())
    })
}

/// Searches memory entries by keyword query.
///
/// Returns up to `limit` entries ranked by relevance. The `score` field
/// on each entry indicates the match quality.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// the memory backend is not available, or [`FfiError::SpawnError`]
/// on backend access failure.
pub(crate) fn recall_memory_inner(
    query: String,
    limit: u32,
) -> Result<Vec<FfiMemoryEntry>, FfiError> {
    crate::runtime::with_memory(|memory, handle| {
        let entries = handle
            .block_on(memory.recall(&query, limit as usize))
            .map_err(|e| FfiError::SpawnError {
                detail: format!("memory recall failed: {e}"),
            })?;
        Ok(entries.iter().map(to_ffi).collect())
    })
}

/// Deletes a memory entry by key.
///
/// Returns `true` if the entry was found and deleted, `false` otherwise.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// the memory backend is not available, or [`FfiError::SpawnError`]
/// on backend access failure.
pub(crate) fn forget_memory_inner(key: String) -> Result<bool, FfiError> {
    crate::runtime::with_memory(|memory, handle| {
        handle
            .block_on(memory.forget(&key))
            .map_err(|e| FfiError::SpawnError {
                detail: format!("memory forget failed: {e}"),
            })
    })
}

/// Returns the total number of memory entries.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running or
/// the memory backend is not available, or [`FfiError::SpawnError`]
/// on backend access failure.
pub(crate) fn memory_count_inner() -> Result<u32, FfiError> {
    crate::runtime::with_memory(|memory, handle| {
        let count = handle
            .block_on(memory.count())
            .map_err(|e| FfiError::SpawnError {
                detail: format!("memory count failed: {e}"),
            })?;
        Ok(u32::try_from(count).unwrap_or(u32::MAX))
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_list_memories_not_running() {
        let result = list_memories_inner(None, 100);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_recall_memory_not_running() {
        let result = recall_memory_inner("test query".into(), 10);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_forget_memory_not_running() {
        let result = forget_memory_inner("test-key".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_memory_count_not_running() {
        let result = memory_count_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_parse_category_core() {
        assert!(matches!(
            parse_category("core"),
            zeroclaw::memory::MemoryCategory::Core
        ));
    }

    #[test]
    fn test_parse_category_daily() {
        assert!(matches!(
            parse_category("daily"),
            zeroclaw::memory::MemoryCategory::Daily
        ));
    }

    #[test]
    fn test_parse_category_conversation() {
        assert!(matches!(
            parse_category("conversation"),
            zeroclaw::memory::MemoryCategory::Conversation
        ));
    }

    #[test]
    fn test_parse_category_custom() {
        let cat = parse_category("project_notes");
        assert!(matches!(
            cat,
            zeroclaw::memory::MemoryCategory::Custom(ref s) if s == "project_notes"
        ));
    }

    #[test]
    fn test_to_ffi_conversion() {
        let entry = zeroclaw::memory::MemoryEntry {
            id: "id-1".into(),
            key: "favourite_lang".into(),
            content: "Rust".into(),
            category: zeroclaw::memory::MemoryCategory::Core,
            timestamp: "2026-02-18T12:00:00Z".into(),
            session_id: Some("session-1".into()),
            score: Some(0.95),
        };

        let ffi = to_ffi(&entry);
        assert_eq!(ffi.id, "id-1");
        assert_eq!(ffi.key, "favourite_lang");
        assert_eq!(ffi.content, "Rust");
        assert_eq!(ffi.category, "core");
        assert_eq!(ffi.timestamp, "2026-02-18T12:00:00Z");
        assert_eq!(ffi.score, Some(0.95));
    }
}
