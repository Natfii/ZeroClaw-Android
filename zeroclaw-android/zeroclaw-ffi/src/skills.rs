/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Skills browsing and management for the Android dashboard.
//!
//! Wraps the upstream `zeroclaw::skills` module behind [`FfiSkill`] and
//! [`FfiSkillTool`] records with inner functions suitable for export via
//! UniFFI.

use crate::error::FfiError;

/// A skill loaded from the workspace or community repository.
///
/// Maps to the upstream [`zeroclaw::skills::Skill`] struct but flattens
/// the `tools` vector into counts and names for lightweight FFI transfer.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiSkill {
    /// Display name of the skill.
    pub name: String,
    /// Human-readable description.
    pub description: String,
    /// Semantic version string.
    pub version: String,
    /// Optional author name or identifier.
    pub author: Option<String>,
    /// Tags for categorisation (e.g. `"automation"`, `"devops"`).
    pub tags: Vec<String>,
    /// Number of tools provided by this skill.
    pub tool_count: u32,
    /// Names of the tools provided by this skill.
    pub tool_names: Vec<String>,
}

/// A single tool defined by a skill.
///
/// Maps to the upstream [`zeroclaw::skills::SkillTool`] struct with the
/// `args` map omitted for FFI simplicity.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiSkillTool {
    /// Unique tool name within the skill.
    pub name: String,
    /// Human-readable tool description.
    pub description: String,
    /// Tool kind: `"shell"`, `"http"`, or `"script"`.
    pub kind: String,
    /// Command string, URL, or script path.
    pub command: String,
}

/// Converts an upstream [`zeroclaw::skills::Skill`] to an [`FfiSkill`].
fn to_ffi(skill: &zeroclaw::skills::Skill) -> FfiSkill {
    FfiSkill {
        name: skill.name.clone(),
        description: skill.description.clone(),
        version: skill.version.clone(),
        author: skill.author.clone(),
        tags: skill.tags.clone(),
        tool_count: u32::try_from(skill.tools.len()).unwrap_or(u32::MAX),
        tool_names: skill.tools.iter().map(|t| t.name.clone()).collect(),
    }
}

/// Lists all skills loaded from the workspace directory.
///
/// Reads the workspace path from the daemon config. Returns an empty
/// vector if no skills are installed or the skills directory does not
/// exist.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn list_skills_inner() -> Result<Vec<FfiSkill>, FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    let skills = zeroclaw::skills::load_skills(&workspace_dir);
    Ok(skills.iter().map(to_ffi).collect())
}

/// Lists the tools provided by a specific skill.
///
/// Returns an empty vector if the skill is not found or has no tools.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn get_skill_tools_inner(skill_name: String) -> Result<Vec<FfiSkillTool>, FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    let skills = zeroclaw::skills::load_skills(&workspace_dir);
    let tools = skills
        .iter()
        .find(|s| s.name == skill_name)
        .map_or_else(Vec::new, |s| {
            s.tools
                .iter()
                .map(|t| FfiSkillTool {
                    name: t.name.clone(),
                    description: t.description.clone(),
                    kind: t.kind.clone(),
                    command: t.command.clone(),
                })
                .collect()
        });
    Ok(tools)
}

/// Installs a skill from a URL or local path.
///
/// Delegates to `zeroclaw::skills::handle_command` with the `Install`
/// variant.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running, or
/// [`FfiError::SpawnError`] if the installation fails (e.g. git clone
/// failure, invalid path).
pub(crate) fn install_skill_inner(source: String) -> Result<(), FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    zeroclaw::skills::handle_command(zeroclaw::SkillCommands::Install { source }, &workspace_dir)
        .map_err(|e| FfiError::SpawnError {
            detail: format!("skill install failed: {e}"),
        })
}

/// Removes an installed skill by name.
///
/// Delegates to `zeroclaw::skills::handle_command` with the `Remove`
/// variant.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running, or
/// [`FfiError::SpawnError`] if removal fails (e.g. skill not found,
/// path traversal rejected).
pub(crate) fn remove_skill_inner(name: String) -> Result<(), FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    zeroclaw::skills::handle_command(zeroclaw::SkillCommands::Remove { name }, &workspace_dir)
        .map_err(|e| FfiError::SpawnError {
            detail: format!("skill remove failed: {e}"),
        })
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_list_skills_not_running() {
        let result = list_skills_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_get_skill_tools_not_running() {
        let result = get_skill_tools_inner("test".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_install_skill_not_running() {
        let result = install_skill_inner("https://example.com/skill".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_remove_skill_not_running() {
        let result = remove_skill_inner("test-skill".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_to_ffi_conversion() {
        let skill = zeroclaw::skills::Skill {
            name: "test-skill".into(),
            description: "A test skill".into(),
            version: "1.0.0".into(),
            author: Some("tester".into()),
            tags: vec!["test".into(), "demo".into()],
            tools: vec![
                zeroclaw::skills::SkillTool {
                    name: "tool-a".into(),
                    description: "Tool A".into(),
                    kind: "shell".into(),
                    command: "echo a".into(),
                    args: std::collections::HashMap::new(),
                },
                zeroclaw::skills::SkillTool {
                    name: "tool-b".into(),
                    description: "Tool B".into(),
                    kind: "http".into(),
                    command: "https://example.com".into(),
                    args: std::collections::HashMap::new(),
                },
            ],
            prompts: vec![],
            location: None,
        };

        let ffi = to_ffi(&skill);
        assert_eq!(ffi.name, "test-skill");
        assert_eq!(ffi.description, "A test skill");
        assert_eq!(ffi.version, "1.0.0");
        assert_eq!(ffi.author.as_deref(), Some("tester"));
        assert_eq!(ffi.tags, vec!["test", "demo"]);
        assert_eq!(ffi.tool_count, 2);
        assert_eq!(ffi.tool_names, vec!["tool-a", "tool-b"]);
    }

    #[test]
    fn test_to_ffi_minimal() {
        let skill = zeroclaw::skills::Skill {
            name: "minimal".into(),
            description: "Bare minimum".into(),
            version: "0.1.0".into(),
            author: None,
            tags: vec![],
            tools: vec![],
            prompts: vec![],
            location: None,
        };

        let ffi = to_ffi(&skill);
        assert_eq!(ffi.tool_count, 0);
        assert!(ffi.tool_names.is_empty());
        assert!(ffi.author.is_none());
    }
}
