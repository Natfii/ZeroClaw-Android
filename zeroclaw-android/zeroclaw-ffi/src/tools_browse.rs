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
#[derive(Debug, Clone, serde::Serialize, uniffi::Record)]
pub struct FfiToolSpec {
    /// Unique tool name (e.g. `"shell"`, `"file_read"`).
    pub name: String,
    /// Human-readable description of the tool.
    pub description: String,
    /// Origin of the tool: `"built-in"` or the skill name.
    pub source: String,
    /// JSON schema for the tool parameters, or `"{}"` if unavailable.
    pub parameters_json: String,
    /// Whether the tool is usable in the current Android session.
    ///
    /// Session-available tools (memory, cron, web tools) are active.
    /// Tools requiring a `SecurityPolicy` (shell, file I/O, git) are
    /// inactive because they can only execute via daemon channel routing.
    pub is_active: bool,
    /// Human-readable reason the tool is inactive, or empty string when active.
    ///
    /// Common values:
    /// - `""` -- tool is active
    /// - `"Available via daemon channels only"` -- requires `SecurityPolicy`
    /// - `"Disabled in settings"` -- config flag is off
    pub inactive_reason: String,
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

/// Tools available in the Android session without a [`SecurityPolicy`].
///
/// Memory and cron tools run directly in the FFI session and are always
/// active when the daemon is running.
const SESSION_TOOLS: &[&str] = &[
    "memory_store",
    "memory_recall",
    "memory_forget",
    "schedule",
];

/// Tools that require a [`SecurityPolicy`] and can only execute via daemon
/// channel routing (e.g. Telegram, Discord).
///
/// These are listed in the tool browser for visibility but cannot be
/// invoked from the Android session directly.
///
/// Used in tests to validate that every core tool is classified as either
/// a session tool or a security-policy tool.
#[cfg(test)]
const SECURITY_POLICY_TOOLS: &[&str] = &[
    "shell",
    "file_read",
    "file_write",
    "git_operations",
    "screenshot",
    "image_info",
];

/// Inactive reason for tools that require daemon channel routing.
const REASON_DAEMON_ONLY: &str = "Available via daemon channels only";

/// Converts a [`BuiltInTool`] to an [`FfiToolSpec`] with `"built-in"` source.
///
/// The default active status is determined by whether the tool name appears
/// in [`SESSION_TOOLS`] (active) or [`SECURITY_POLICY_TOOLS`] (inactive).
/// Conditional tools (browser, HTTP, composio, delegate) default to inactive
/// and are overridden to active when added by [`list_tools_inner`].
fn builtin_to_spec(tool: &BuiltInTool) -> FfiToolSpec {
    let is_session = SESSION_TOOLS.contains(&tool.name);
    FfiToolSpec {
        name: tool.name.to_string(),
        description: tool.description.to_string(),
        source: "built-in".to_string(),
        parameters_json: "{}".to_string(),
        is_active: is_session,
        inactive_reason: if is_session {
            String::new()
        } else {
            REASON_DAEMON_ONLY.to_string()
        },
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
        specs.extend(BROWSER_TOOLS.iter().map(|t| {
            let mut s = builtin_to_spec(t);
            s.is_active = true;
            s.inactive_reason = String::new();
            s
        }));
    }

    if http_enabled {
        let mut s = builtin_to_spec(&HTTP_TOOL);
        s.is_active = true;
        s.inactive_reason = String::new();
        specs.push(s);
    }

    if composio_key.as_ref().is_some_and(|k| !k.is_empty()) {
        let mut s = builtin_to_spec(&COMPOSIO_TOOL);
        s.is_active = true;
        s.inactive_reason = String::new();
        specs.push(s);
    }

    if has_agents {
        let mut s = builtin_to_spec(&DELEGATE_TOOL);
        s.is_active = true;
        s.inactive_reason = String::new();
        specs.push(s);
    }

    let skills = crate::skills::load_skills_from_workspace(&workspace_dir);
    for (skill, tools) in &skills {
        for tool in tools {
            specs.push(FfiToolSpec {
                name: tool.name.clone(),
                description: tool.description.clone(),
                source: skill.name.clone(),
                parameters_json: "{}".to_string(),
                is_active: true,
                inactive_reason: String::new(),
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

    #[test]
    fn test_session_tools_are_active() {
        for &name in SESSION_TOOLS {
            let tool = CORE_TOOLS
                .iter()
                .find(|t| t.name == name)
                .unwrap_or_else(|| panic!("session tool {name} missing from CORE_TOOLS"));
            let spec = builtin_to_spec(tool);
            assert!(spec.is_active, "{name} should be active");
            assert!(
                spec.inactive_reason.is_empty(),
                "{name} should have empty inactive_reason"
            );
        }
    }

    #[test]
    fn test_security_policy_tools_are_inactive() {
        for &name in SECURITY_POLICY_TOOLS {
            let tool = CORE_TOOLS
                .iter()
                .find(|t| t.name == name)
                .unwrap_or_else(|| panic!("security tool {name} missing from CORE_TOOLS"));
            let spec = builtin_to_spec(tool);
            assert!(!spec.is_active, "{name} should be inactive");
            assert_eq!(
                spec.inactive_reason, REASON_DAEMON_ONLY,
                "{name} should have daemon-only reason"
            );
        }
    }

    #[test]
    fn test_session_and_security_cover_all_core_tools() {
        for tool in CORE_TOOLS {
            assert!(
                SESSION_TOOLS.contains(&tool.name)
                    || SECURITY_POLICY_TOOLS.contains(&tool.name),
                "core tool {:?} is in neither SESSION_TOOLS nor SECURITY_POLICY_TOOLS",
                tool.name
            );
        }
    }

    #[test]
    fn test_conditional_tools_default_inactive() {
        let http = builtin_to_spec(&HTTP_TOOL);
        assert!(!http.is_active, "http_request should default to inactive");
        assert_eq!(http.inactive_reason, REASON_DAEMON_ONLY);

        let composio = builtin_to_spec(&COMPOSIO_TOOL);
        assert!(
            !composio.is_active,
            "composio should default to inactive"
        );

        let delegate = builtin_to_spec(&DELEGATE_TOOL);
        assert!(
            !delegate.is_active,
            "delegate should default to inactive"
        );

        for browser_tool in BROWSER_TOOLS {
            let spec = builtin_to_spec(browser_tool);
            assert!(
                !spec.is_active,
                "{} should default to inactive",
                browser_tool.name
            );
        }
    }
}
