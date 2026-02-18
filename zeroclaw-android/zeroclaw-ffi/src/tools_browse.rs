/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Tool inventory browsing for the Android dashboard.
//!
//! Enumerates all available tools from the daemon config and installed
//! skills without instantiating the actual tool objects (which require
//! runtime dependencies like security policies and memory backends).

use crate::error::FfiError;

/// A tool specification suitable for display in the Android tools browser.
///
/// Contains metadata about a tool without the actual tool instance, making
/// it safe and lightweight for FFI transfer.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FfiToolSpec {
    /// Unique tool name (e.g. `"shell"`, `"file_read"`).
    pub name: String,
    /// Human-readable description of the tool.
    pub description: String,
    /// Origin of the tool: `"built-in"` or the skill name.
    pub source: String,
    /// JSON schema for the tool parameters, or `"{}"` if unavailable.
    pub parameters_json: String,
}

/// Describes a built-in tool with a static name and description.
struct BuiltInTool {
    /// Tool name as registered in the tool registry.
    name: &'static str,
    /// Brief description of what the tool does.
    description: &'static str,
}

/// Static list of all core built-in tools.
///
/// These tools are always available when the daemon is running.
const CORE_TOOLS: &[BuiltInTool] = &[
    BuiltInTool {
        name: "shell",
        description: "Execute shell commands with security policy enforcement",
    },
    BuiltInTool {
        name: "file_read",
        description: "Read file contents with path validation",
    },
    BuiltInTool {
        name: "file_write",
        description: "Write content to files with path validation",
    },
    BuiltInTool {
        name: "memory_store",
        description: "Store a key-value pair in the memory backend",
    },
    BuiltInTool {
        name: "memory_recall",
        description: "Recall memories matching a keyword query",
    },
    BuiltInTool {
        name: "memory_forget",
        description: "Remove a memory entry by key",
    },
    BuiltInTool {
        name: "schedule",
        description: "Schedule cron jobs and one-shot delayed tasks",
    },
    BuiltInTool {
        name: "git_operations",
        description: "Perform git operations in the workspace directory",
    },
    BuiltInTool {
        name: "screenshot",
        description: "Capture screenshots with security policy enforcement",
    },
    BuiltInTool {
        name: "image_info",
        description: "Extract metadata and dimensions from image files",
    },
];

/// Optional tools that depend on config flags.
const BROWSER_TOOLS: &[BuiltInTool] = &[
    BuiltInTool {
        name: "browser_open",
        description: "Open a URL in a headless or remote browser",
    },
    BuiltInTool {
        name: "browser",
        description: "Full browser automation (navigation, clicks, screenshots)",
    },
];

/// HTTP request tool (available when HTTP is enabled).
const HTTP_TOOL: BuiltInTool = BuiltInTool {
    name: "http_request",
    description: "Make HTTP requests with domain allowlist enforcement",
};

/// Composio integration tool (available when Composio API key is set).
const COMPOSIO_TOOL: BuiltInTool = BuiltInTool {
    name: "composio",
    description: "Access Composio integrations for third-party APIs",
};

/// Delegate tool (available when agent delegation is configured).
const DELEGATE_TOOL: BuiltInTool = BuiltInTool {
    name: "delegate",
    description: "Delegate tasks to sub-agents with independent context",
};

/// Converts a [`BuiltInTool`] to an [`FfiToolSpec`] with `"built-in"` source.
fn builtin_to_spec(tool: &BuiltInTool) -> FfiToolSpec {
    FfiToolSpec {
        name: tool.name.to_string(),
        description: tool.description.to_string(),
        source: "built-in".to_string(),
        parameters_json: "{}".to_string(),
    }
}

/// Lists all available tools based on daemon configuration and installed skills.
///
/// Enumerates built-in tools that are always active, conditionally adds
/// browser/HTTP/Composio/delegate tools based on config flags, then
/// appends tools from all installed skills.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if the daemon is not running.
pub(crate) fn list_tools_inner() -> Result<Vec<FfiToolSpec>, FfiError> {
    let (workspace_dir, browser_enabled, http_enabled, composio_key, has_agents) =
        crate::runtime::with_daemon_config(|config| {
            (
                config.workspace_dir.clone(),
                config.browser.enabled,
                config.http_request.enabled,
                config.composio.api_key.clone(),
                !config.agents.is_empty(),
            )
        })?;

    let mut specs: Vec<FfiToolSpec> = CORE_TOOLS.iter().map(builtin_to_spec).collect();

    if browser_enabled {
        specs.extend(BROWSER_TOOLS.iter().map(builtin_to_spec));
    }

    if http_enabled {
        specs.push(builtin_to_spec(&HTTP_TOOL));
    }

    if composio_key.as_ref().is_some_and(|k| !k.is_empty()) {
        specs.push(builtin_to_spec(&COMPOSIO_TOOL));
    }

    if has_agents {
        specs.push(builtin_to_spec(&DELEGATE_TOOL));
    }

    let skills = zeroclaw::skills::load_skills(&workspace_dir);
    for skill in &skills {
        for tool in &skill.tools {
            specs.push(FfiToolSpec {
                name: tool.name.clone(),
                description: tool.description.clone(),
                source: skill.name.clone(),
                parameters_json: "{}".to_string(),
            });
        }
    }

    Ok(specs)
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    #[test]
    fn test_list_tools_not_running() {
        let result = list_tools_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("not running"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_core_tools_count() {
        assert_eq!(CORE_TOOLS.len(), 10);
    }

    #[test]
    fn test_builtin_to_spec() {
        let tool = &CORE_TOOLS[0];
        let spec = builtin_to_spec(tool);
        assert_eq!(spec.name, "shell");
        assert_eq!(spec.source, "built-in");
        assert_eq!(spec.parameters_json, "{}");
        assert!(!spec.description.is_empty());
    }

    #[test]
    fn test_browser_tools_count() {
        assert_eq!(BROWSER_TOOLS.len(), 2);
    }
}
