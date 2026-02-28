/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Skills browsing and management for the Android dashboard.
//!
//! Upstream v0.1.6 made the `zeroclaw::skills` module `pub(crate)`,
//! so skill loading and management now use filesystem-based scanning
//! of the workspace skills directory. Install and remove operations
//! are not available until the upstream exposes a gateway API for them.

use crate::error::FfiError;

/// A skill loaded from the workspace skills directory.
///
/// Fields are populated by scanning `skill.toml` manifests from the
/// workspace directory, since the upstream `Skill` type is no longer
/// accessible from outside the crate.
#[derive(Debug, Clone, serde::Serialize, uniffi::Record)]
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
#[derive(Debug, Clone, serde::Serialize, uniffi::Record)]
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

/// Internal representation of a skill parsed from a TOML manifest.
#[derive(Debug, serde::Deserialize)]
pub(crate) struct SkillManifest {
    #[serde(default)]
    pub(crate) name: String,
    #[serde(default)]
    pub(crate) description: String,
    #[serde(default)]
    pub(crate) version: String,
    #[serde(default)]
    pub(crate) author: Option<String>,
    #[serde(default)]
    pub(crate) tags: Vec<String>,
    #[serde(default)]
    pub(crate) tools: Vec<ToolManifest>,
}

/// Internal representation of a tool within a skill manifest.
#[derive(Debug, serde::Deserialize)]
pub(crate) struct ToolManifest {
    #[serde(default)]
    pub(crate) name: String,
    #[serde(default)]
    pub(crate) description: String,
    #[serde(default)]
    pub(crate) kind: String,
    #[serde(default)]
    pub(crate) command: String,
}

/// Scans the workspace skills directory for skill manifests.
///
/// Reads `skill.toml` from each subdirectory of `{workspace}/skills/`.
/// Returns an empty vec if the directory doesn't exist or has no skills.
pub(crate) fn load_skills_from_workspace(
    workspace_dir: &std::path::Path,
) -> Vec<(SkillManifest, Vec<ToolManifest>)> {
    let skills_dir = workspace_dir.join("skills");
    let Ok(entries) = std::fs::read_dir(&skills_dir) else {
        return Vec::new();
    };

    let mut result = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        let manifest_path = path.join("skill.toml");
        if let Ok(content) = std::fs::read_to_string(&manifest_path)
            && let Ok(manifest) = toml::from_str::<SkillManifest>(&content)
        {
            let tools = manifest.tools;
            let skill = SkillManifest {
                name: if manifest.name.is_empty() {
                    entry.file_name().to_string_lossy().into_owned()
                } else {
                    manifest.name
                },
                description: manifest.description,
                version: manifest.version,
                author: manifest.author,
                tags: manifest.tags,
                tools: Vec::new(),
            };
            result.push((skill, tools));
        }
    }
    result
}

/// Lists all skills loaded from the workspace directory.
///
/// Reads skill manifests from `{workspace}/skills/` subdirectories.
/// Returns an empty vector if no skills are installed.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn list_skills_inner() -> Result<Vec<FfiSkill>, FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    let skills = load_skills_from_workspace(&workspace_dir);
    Ok(skills
        .iter()
        .map(|(skill, tools)| FfiSkill {
            name: skill.name.clone(),
            description: skill.description.clone(),
            version: skill.version.clone(),
            author: skill.author.clone(),
            tags: skill.tags.clone(),
            tool_count: u32::try_from(tools.len()).unwrap_or(u32::MAX),
            tool_names: tools.iter().map(|t| t.name.clone()).collect(),
        })
        .collect())
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
    let skills = load_skills_from_workspace(&workspace_dir);
    let tools = skills
        .iter()
        .find(|(s, _)| s.name == skill_name)
        .map_or_else(Vec::new, |(_, tools)| {
            tools
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
/// For URLs (starting with `http://` or `https://`), runs `git clone
/// --depth 1` into the workspace `skills/` directory. For local paths,
/// copies the directory tree recursively.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] if the git clone or copy fails,
/// [`FfiError::ConfigError`] if the source skill has no manifest.
pub(crate) fn install_skill_inner(source: String) -> Result<(), FfiError> {
    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    let skills_dir = workspace_dir.join("skills");
    std::fs::create_dir_all(&skills_dir).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to create skills directory: {e}"),
    })?;

    if source.starts_with("http://") || source.starts_with("https://") {
        install_skill_from_url(&source, &skills_dir)
    } else {
        install_skill_from_path(&source, &skills_dir)
    }
}

/// Clones a skill from a git URL into the skills directory.
fn install_skill_from_url(url: &str, skills_dir: &std::path::Path) -> Result<(), FfiError> {
    let repo_name = url
        .rsplit('/')
        .next()
        .unwrap_or("skill")
        .trim_end_matches(".git");
    if repo_name.is_empty() || repo_name.contains("..") {
        return Err(FfiError::ConfigError {
            detail: format!("invalid skill URL: {url}"),
        });
    }

    let dest = skills_dir.join(repo_name);
    if dest.exists() {
        return Err(FfiError::SpawnError {
            detail: format!("skill already installed: {repo_name}"),
        });
    }

    let output = std::process::Command::new("git")
        .args(["clone", "--depth", "1", url])
        .arg(&dest)
        .output()
        .map_err(|e| FfiError::SpawnError {
            detail: format!("failed to run git clone: {e}"),
        })?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(FfiError::SpawnError {
            detail: format!("git clone failed: {stderr}"),
        });
    }

    if !dest.join("skill.toml").exists() {
        let _ = std::fs::remove_dir_all(&dest);
        return Err(FfiError::ConfigError {
            detail: format!("cloned repository has no skill.toml manifest: {url}"),
        });
    }

    Ok(())
}

/// Copies a skill from a local path into the skills directory.
fn install_skill_from_path(source: &str, skills_dir: &std::path::Path) -> Result<(), FfiError> {
    let src_path = std::path::Path::new(source);
    if !src_path.is_dir() {
        return Err(FfiError::ConfigError {
            detail: format!("source is not a directory: {source}"),
        });
    }

    if !src_path.join("skill.toml").exists() {
        return Err(FfiError::ConfigError {
            detail: format!("source directory has no skill.toml manifest: {source}"),
        });
    }

    let dir_name = src_path.file_name().ok_or_else(|| FfiError::ConfigError {
        detail: format!("cannot determine directory name from: {source}"),
    })?;

    let dest = skills_dir.join(dir_name);
    if dest.exists() {
        return Err(FfiError::SpawnError {
            detail: format!("skill already installed: {}", dir_name.to_string_lossy()),
        });
    }

    copy_dir_recursive(src_path, &dest).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to copy skill directory: {e}"),
    })
}

/// Recursively copies a directory tree.
fn copy_dir_recursive(src: &std::path::Path, dest: &std::path::Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dest)?;
    for entry in std::fs::read_dir(src)? {
        let entry = entry?;
        let entry_dest = dest.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            copy_dir_recursive(&entry.path(), &entry_dest)?;
        } else {
            std::fs::copy(entry.path(), entry_dest)?;
        }
    }
    Ok(())
}

/// Removes an installed skill by name.
///
/// Deletes the skill directory from the workspace's `skills/` folder.
/// Path traversal attempts (e.g. `../`) are rejected.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::ConfigError`] if the name contains path traversal, or
/// [`FfiError::SpawnError`] if the skill is not found or deletion fails.
pub(crate) fn remove_skill_inner(name: String) -> Result<(), FfiError> {
    if name.contains("..") || name.contains('/') || name.contains('\\') {
        return Err(FfiError::ConfigError {
            detail: format!("invalid skill name (path traversal rejected): {name}"),
        });
    }

    let workspace_dir = crate::runtime::with_daemon_config(|config| config.workspace_dir.clone())?;
    let skill_dir = workspace_dir.join("skills").join(&name);

    if !skill_dir.is_dir() {
        return Err(FfiError::SpawnError {
            detail: format!("skill not found: {name}"),
        });
    }

    std::fs::remove_dir_all(&skill_dir).map_err(|e| FfiError::SpawnError {
        detail: format!("failed to remove skill directory: {e}"),
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
    }

    #[test]
    fn test_remove_skill_not_running() {
        let result = remove_skill_inner("test-skill".into());
        assert!(result.is_err());
    }

    #[test]
    fn test_remove_skill_path_traversal_rejected() {
        let result = remove_skill_inner("../etc".into());
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("path traversal"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_install_skill_from_local_path() {
        let temp = std::env::temp_dir().join("zeroclaw_test_install_skill");
        let source_dir = temp.join("source-skill");
        let _ = std::fs::remove_dir_all(&temp);
        std::fs::create_dir_all(&source_dir).unwrap();
        std::fs::write(
            source_dir.join("skill.toml"),
            "name = \"installed-skill\"\ndescription = \"test\"\nversion = \"1.0.0\"\n",
        )
        .unwrap();

        let skills_dir = temp.join("skills");
        std::fs::create_dir_all(&skills_dir).unwrap();

        let result = install_skill_from_path(&source_dir.to_string_lossy(), &skills_dir);
        assert!(result.is_ok());
        assert!(skills_dir.join("source-skill").join("skill.toml").exists());

        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_install_skill_from_path_no_manifest() {
        let temp = std::env::temp_dir().join("zeroclaw_test_install_no_manifest");
        let source_dir = temp.join("bad-skill");
        let _ = std::fs::remove_dir_all(&temp);
        std::fs::create_dir_all(&source_dir).unwrap();

        let skills_dir = temp.join("skills");
        std::fs::create_dir_all(&skills_dir).unwrap();

        let result = install_skill_from_path(&source_dir.to_string_lossy(), &skills_dir);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("no skill.toml"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }

        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_install_skill_already_exists() {
        let temp = std::env::temp_dir().join("zeroclaw_test_install_exists");
        let source_dir = temp.join("dup-skill");
        let _ = std::fs::remove_dir_all(&temp);
        std::fs::create_dir_all(&source_dir).unwrap();
        std::fs::write(
            source_dir.join("skill.toml"),
            "name = \"dup\"\nversion = \"1.0.0\"\n",
        )
        .unwrap();

        let skills_dir = temp.join("skills");
        std::fs::create_dir_all(skills_dir.join("dup-skill")).unwrap();

        let result = install_skill_from_path(&source_dir.to_string_lossy(), &skills_dir);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::SpawnError { detail } => {
                assert!(detail.contains("already installed"));
            }
            other => panic!("expected SpawnError, got {other:?}"),
        }

        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_copy_dir_recursive() {
        let temp = std::env::temp_dir().join("zeroclaw_test_copy_dir");
        let _ = std::fs::remove_dir_all(&temp);
        let src = temp.join("src");
        let sub = src.join("sub");
        std::fs::create_dir_all(&sub).unwrap();
        std::fs::write(src.join("a.txt"), "hello").unwrap();
        std::fs::write(sub.join("b.txt"), "world").unwrap();

        let dest = temp.join("dest");
        copy_dir_recursive(&src, &dest).unwrap();

        assert!(dest.join("a.txt").exists());
        assert!(dest.join("sub").join("b.txt").exists());
        assert_eq!(
            std::fs::read_to_string(dest.join("a.txt")).unwrap(),
            "hello"
        );

        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_load_skills_empty_dir() {
        let temp = std::env::temp_dir().join("zeroclaw_test_skills_empty");
        let _ = std::fs::create_dir_all(&temp);
        let result = load_skills_from_workspace(&temp);
        assert!(result.is_empty());
        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_load_skills_with_manifest() {
        let temp = std::env::temp_dir().join("zeroclaw_test_skills_manifest");
        let skill_dir = temp.join("skills").join("test-skill");
        let _ = std::fs::create_dir_all(&skill_dir);
        std::fs::write(
            skill_dir.join("skill.toml"),
            r#"
name = "test-skill"
description = "A test skill"
version = "1.0.0"
author = "tester"
tags = ["test"]

[[tools]]
name = "tool-a"
description = "Tool A"
kind = "shell"
command = "echo a"
"#,
        )
        .unwrap();

        let result = load_skills_from_workspace(&temp);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].0.name, "test-skill");
        assert_eq!(result[0].1.len(), 1);
        assert_eq!(result[0].1[0].name, "tool-a");

        let _ = std::fs::remove_dir_all(&temp);
    }
}
