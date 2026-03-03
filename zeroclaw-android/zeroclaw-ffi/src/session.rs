/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Live agent session management with streaming tool-call loop integration.
//!
//! A session represents a single multi-turn conversation with the `ZeroClaw`
//! agent loop. The lifecycle follows a strict state machine:
//!
//! 1. **Start** -- [`session_start`](crate::session_start) creates a new
//!    session, parsing daemon config and building the system prompt.
//! 2. **Seed** -- optional: inject prior context via
//!    [`session_seed_history`](crate::session_seed_history).
//! 3. **Send** -- [`session_send`](crate::session_send) runs the full
//!    tool-call loop, streaming progress deltas through an
//!    [`FfiSessionListener`] callback.
//! 4. **Cancel / Clear** -- abort the current send or wipe history.
//! 5. **History** -- [`session_history`](crate::session_history) returns
//!    the conversation transcript.
//! 6. **Destroy** -- [`session_destroy`](crate::session_destroy) tears
//!    down the session and releases all resources.
//!
//! Only one session exists at a time (guarded by the [`SESSION`] mutex).

// Remove these allows once all session functions are wired in lib.rs.
#![allow(dead_code)]

use std::fmt::Write;
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use tokio_util::sync::CancellationToken;
use zeroclaw::memory::{Memory, MemoryCategory};
use zeroclaw::providers::{ChatMessage, ChatRequest, Provider};
use zeroclaw::tools::{Tool, ToolResult, ToolSpec};

use crate::error::FfiError;
use crate::runtime::{clone_daemon_config, clone_daemon_memory};

/// Maximum user message size in bytes (1 MiB).
const MAX_MESSAGE_BYTES: usize = 1_048_576;

/// Default maximum agentic tool-use iterations per user message.
const DEFAULT_MAX_TOOL_ITERATIONS: usize = 10;

/// Non-system message count threshold that triggers auto-compaction.
const DEFAULT_MAX_HISTORY_MESSAGES: usize = 50;

/// Number of most-recent non-system messages to keep after compaction.
const COMPACTION_KEEP_RECENT: usize = 20;

/// Safety cap for the compaction source transcript sent to the summariser.
const COMPACTION_MAX_SOURCE_CHARS: usize = 12_000;

/// Maximum characters retained in the stored compaction summary.
const COMPACTION_MAX_SUMMARY_CHARS: usize = 2_000;

/// Minimum characters per chunk when streaming the final response text.
const STREAM_CHUNK_MIN_CHARS: usize = 80;

/// Maximum number of seed messages accepted by [`session_seed_inner`].
const MAX_SEED_MESSAGES: usize = 20;

/// The global singleton session slot.
///
/// At most one [`Session`] is active at any time. Operations that require
/// a running session acquire this mutex and return
/// [`FfiError::StateError`] when the slot is `None`.
static SESSION: Mutex<Option<Session>> = Mutex::new(None);

/// The cancellation token for the currently active [`session_send_inner`] call.
///
/// Set at the start of `session_send_inner`, cleared on exit. Calling
/// [`session_cancel_inner`] cancels the token, causing the agent loop
/// to abort at the next check point.
static CANCEL_TOKEN: Mutex<Option<CancellationToken>> = Mutex::new(None);

/// Locks the [`SESSION`] mutex, recovering from poison if a prior holder panicked.
///
/// See [`crate::runtime::lock_daemon`] for the rationale behind poison recovery.
fn lock_session() -> std::sync::MutexGuard<'static, Option<Session>> {
    SESSION.lock().unwrap_or_else(|e| {
        tracing::warn!("Session mutex was poisoned; recovering: {e}");
        e.into_inner()
    })
}

/// Locks the [`CANCEL_TOKEN`] mutex, recovering from poison.
fn lock_cancel_token() -> std::sync::MutexGuard<'static, Option<CancellationToken>> {
    CANCEL_TOKEN.lock().unwrap_or_else(|e| {
        tracing::warn!("Cancel token mutex was poisoned; recovering: {e}");
        e.into_inner()
    })
}

// ── FFI tool implementations ────────────────────────────────────────────
//
// Upstream `SecurityPolicy` is `pub(crate)`, so `MemoryStoreTool` and
// `MemoryForgetTool` cannot be constructed from the FFI crate. The
// wrappers below replicate the upstream logic without the security
// check. On Android the user directly initiates all agent actions, so
// the upstream read-only / rate-limit checks are unnecessary.

/// FFI-specific memory store tool that bypasses `SecurityPolicy`.
///
/// On Android the user directly initiates all agent actions, so the
/// upstream read-only / rate-limit checks are unnecessary. The tool
/// delegates directly to the [`Memory`] backend.
struct FfiMemoryStoreTool {
    /// The memory backend shared with the daemon.
    memory: Arc<dyn Memory>,
}

#[async_trait]
impl Tool for FfiMemoryStoreTool {
    fn name(&self) -> &'static str {
        "memory_store"
    }

    fn description(&self) -> &'static str {
        "Store a fact, preference, or note in long-term memory. \
         Use category 'core' for permanent facts, 'daily' for session notes, \
         'conversation' for chat context, or a custom category name."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "key": {
                    "type": "string",
                    "description": "Unique key for this memory (e.g. 'user_lang', 'project_stack')"
                },
                "content": {
                    "type": "string",
                    "description": "The information to remember"
                },
                "category": {
                    "type": "string",
                    "description": "Memory category: 'core' (permanent), 'daily' (session), \
                                    'conversation' (chat), or a custom category name. \
                                    Defaults to 'core'."
                }
            },
            "required": ["key", "content"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        let key = args
            .get("key")
            .and_then(|v| v.as_str())
            .ok_or_else(|| anyhow::anyhow!("Missing 'key' parameter"))?;

        let content = args
            .get("content")
            .and_then(|v| v.as_str())
            .ok_or_else(|| anyhow::anyhow!("Missing 'content' parameter"))?;

        let category = match args.get("category").and_then(|v| v.as_str()) {
            Some("core") | None => MemoryCategory::Core,
            Some("daily") => MemoryCategory::Daily,
            Some("conversation") => MemoryCategory::Conversation,
            Some(other) => MemoryCategory::Custom(other.to_string()),
        };

        match self.memory.store(key, content, category, None).await {
            Ok(()) => Ok(ToolResult {
                success: true,
                output: format!("Stored memory: {key}"),
                error: None,
            }),
            Err(e) => Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some(format!("Failed to store memory: {e}")),
            }),
        }
    }
}

/// FFI-specific memory forget tool that bypasses `SecurityPolicy`.
///
/// See [`FfiMemoryStoreTool`] for rationale on skipping security checks.
struct FfiMemoryForgetTool {
    /// The memory backend shared with the daemon.
    memory: Arc<dyn Memory>,
}

#[async_trait]
impl Tool for FfiMemoryForgetTool {
    fn name(&self) -> &'static str {
        "memory_forget"
    }

    fn description(&self) -> &'static str {
        "Remove a memory by key. Use to delete outdated facts or sensitive \
         data. Returns whether the memory was found and removed."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "key": {
                    "type": "string",
                    "description": "The key of the memory to forget"
                }
            },
            "required": ["key"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        let key = args
            .get("key")
            .and_then(|v| v.as_str())
            .ok_or_else(|| anyhow::anyhow!("Missing 'key' parameter"))?;

        match self.memory.forget(key).await {
            Ok(true) => Ok(ToolResult {
                success: true,
                output: format!("Forgot memory: {key}"),
                error: None,
            }),
            Ok(false) => Ok(ToolResult {
                success: true,
                output: format!("No memory found with key: {key}"),
                error: None,
            }),
            Err(e) => Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some(format!("Failed to forget memory: {e}")),
            }),
        }
    }
}

/// FFI-specific web fetch tool that bypasses upstream's `SecurityPolicy`.
///
/// Upstream [`WebFetchTool`] requires `Arc<SecurityPolicy>` which is
/// `pub(crate)` and inaccessible from the FFI crate. This wrapper
/// replicates the upstream logic (URL validation, SSRF protection,
/// allowlist/blocklist, HTML-to-text conversion) without the security
/// policy dependency. On Android the user directly initiates all agent
/// actions, so the upstream autonomy/rate-limit checks are unnecessary.
struct FfiWebFetchTool {
    /// Normalized allowed domains for URL validation.
    allowed_domains: Vec<String>,
    /// Normalized blocked domains (takes priority over allowlist).
    blocked_domains: Vec<String>,
    /// Maximum response body size in bytes before truncation.
    max_response_size: usize,
    /// HTTP request timeout in seconds.
    timeout_secs: u64,
}

impl FfiWebFetchTool {
    /// Validates a URL against the configured allowlist, blocklist, and DNS.
    async fn validate_url(&self, raw_url: &str) -> anyhow::Result<String> {
        validate_web_fetch_url_with_dns(raw_url, &self.allowed_domains, &self.blocked_domains).await
    }

    /// Truncates response text to the configured size limit.
    fn truncate_response(&self, text: &str) -> String {
        if text.len() > self.max_response_size {
            let mut truncated = text
                .chars()
                .take(self.max_response_size)
                .collect::<String>();
            truncated.push_str("\n\n... [Response truncated due to size limit] ...");
            truncated
        } else {
            text.to_string()
        }
    }

    /// Reads the response body as text, streaming up to `max_response_size + 1` bytes.
    async fn read_response_text_limited(
        &self,
        response: reqwest::Response,
    ) -> anyhow::Result<String> {
        use futures_util::StreamExt;

        let hard_cap = self.max_response_size.saturating_add(1);
        let mut bytes = Vec::new();
        let mut stream = response.bytes_stream();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result?;
            if append_chunk_with_cap(&mut bytes, &chunk, hard_cap) {
                break;
            }
        }

        Ok(String::from_utf8_lossy(&bytes).into_owned())
    }
}

#[async_trait]
#[allow(clippy::too_many_lines)]
impl Tool for FfiWebFetchTool {
    fn name(&self) -> &'static str {
        "web_fetch"
    }

    fn description(&self) -> &'static str {
        "Fetch a web page and return its content as clean plain text. \
         HTML pages are automatically converted to readable text. \
         JSON and plain text responses are returned as-is. \
         Only GET requests; follows redirects. \
         Security: allowlist-only domains, no local/private hosts."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The HTTP or HTTPS URL to fetch"
                }
            },
            "required": ["url"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        let url = args
            .get("url")
            .and_then(|v| v.as_str())
            .ok_or_else(|| anyhow::anyhow!("Missing 'url' parameter"))?;

        let url = match self.validate_url(url).await {
            Ok(v) => v,
            Err(e) => {
                return Ok(ToolResult {
                    success: false,
                    output: String::new(),
                    error: Some(e.to_string()),
                });
            }
        };

        let timeout_secs = if self.timeout_secs == 0 {
            tracing::warn!("web_fetch: timeout_secs is 0, using safe default of 30s");
            30
        } else {
            self.timeout_secs
        };

        let allowed_domains = self.allowed_domains.clone();
        let blocked_domains = self.blocked_domains.clone();
        let redirect_policy = reqwest::redirect::Policy::custom(move |attempt| {
            if attempt.previous().len() >= 10 {
                return attempt.error(std::io::Error::other("Too many redirects (max 10)"));
            }

            if let Err(err) =
                validate_web_fetch_url(attempt.url().as_str(), &allowed_domains, &blocked_domains)
            {
                return attempt.error(std::io::Error::new(
                    std::io::ErrorKind::PermissionDenied,
                    format!("Blocked redirect target: {err}"),
                ));
            }

            attempt.follow()
        });

        let client = match reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(timeout_secs))
            .connect_timeout(std::time::Duration::from_secs(10))
            .redirect(redirect_policy)
            .user_agent("ZeroClaw/0.1 (web_fetch)")
            .build()
        {
            Ok(c) => c,
            Err(e) => {
                return Ok(ToolResult {
                    success: false,
                    output: String::new(),
                    error: Some(format!("Failed to build HTTP client: {e}")),
                });
            }
        };

        let response = match client.get(&url).send().await {
            Ok(r) => r,
            Err(e) => {
                return Ok(ToolResult {
                    success: false,
                    output: String::new(),
                    error: Some(format!("HTTP request failed: {e}")),
                });
            }
        };

        let status = response.status();
        if !status.is_success() {
            return Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some(format!(
                    "HTTP {} {}",
                    status.as_u16(),
                    status.canonical_reason().unwrap_or("Unknown")
                )),
            });
        }

        let content_type = response
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_lowercase();

        let body_mode = if content_type.contains("text/html") || content_type.is_empty() {
            "html"
        } else if content_type.contains("text/plain")
            || content_type.contains("text/markdown")
            || content_type.contains("application/json")
        {
            "plain"
        } else {
            return Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some(format!(
                    "Unsupported content type: {content_type}. \
                     web_fetch supports text/html, text/plain, text/markdown, \
                     and application/json."
                )),
            });
        };

        let body = match self.read_response_text_limited(response).await {
            Ok(t) => t,
            Err(e) => {
                return Ok(ToolResult {
                    success: false,
                    output: String::new(),
                    error: Some(format!("Failed to read response body: {e}")),
                });
            }
        };

        let text = if body_mode == "html" {
            nanohtml2text::html2text(&body)
        } else {
            body
        };

        let output = self.truncate_response(&text);

        Ok(ToolResult {
            success: true,
            output,
            error: None,
        })
    }
}

// ── Web fetch URL validation helpers ────────────────────────────────────
//
// Replicated from upstream `tools/web_fetch.rs` (module-private functions).
// Upstream functions are `fn` (not `pub`) by design; making them `pub`
// would require modifying the submodule (forbidden). This is a one-time
// cost for ~130 lines of well-tested validation logic.

/// Validates a URL for web fetch: scheme, SSRF, allowlist, blocklist.
///
/// This is the synchronous variant used in redirect-policy closures where
/// async DNS resolution is not possible. DNS rebinding protection is
/// handled separately via [`validate_web_fetch_url_with_dns`].
fn validate_web_fetch_url(
    raw_url: &str,
    allowed_domains: &[String],
    blocked_domains: &[String],
) -> anyhow::Result<String> {
    let url = raw_url.trim();

    if url.is_empty() {
        anyhow::bail!("URL cannot be empty");
    }

    if url.chars().any(char::is_whitespace) {
        anyhow::bail!("URL cannot contain whitespace");
    }

    if !url.starts_with("http://") && !url.starts_with("https://") {
        anyhow::bail!("Only http:// and https:// URLs are allowed");
    }

    if allowed_domains.is_empty() {
        anyhow::bail!(
            "web_fetch tool is enabled but no allowed_domains are configured. \
             Add [web_fetch].allowed_domains in config.toml"
        );
    }

    let host = extract_web_fetch_host(url)?;

    if is_private_or_local_host(&host) {
        anyhow::bail!("Blocked local/private host: {host}");
    }

    if host_matches_list(&host, blocked_domains) {
        anyhow::bail!("Host '{host}' is in web_fetch.blocked_domains");
    }

    if !host_matches_list(&host, allowed_domains) {
        anyhow::bail!("Host '{host}' is not in web_fetch.allowed_domains");
    }

    Ok(url.to_string())
}

/// Validates a URL with async DNS resolution for SSRF rebinding protection.
///
/// Calls [`validate_web_fetch_url`] for basic checks, then performs async
/// DNS resolution via [`tokio::net::lookup_host`] to verify that the host
/// resolves only to globally-routable IP addresses.
async fn validate_web_fetch_url_with_dns(
    raw_url: &str,
    allowed_domains: &[String],
    blocked_domains: &[String],
) -> anyhow::Result<String> {
    let url = validate_web_fetch_url(raw_url, allowed_domains, blocked_domains)?;
    let host = extract_web_fetch_host(&url)?;
    validate_resolved_host_is_public(&host).await?;
    Ok(url)
}

/// Appends `chunk` to `buffer`, stopping at `hard_cap`. Returns `true`
/// when the cap is reached.
fn append_chunk_with_cap(buffer: &mut Vec<u8>, chunk: &[u8], hard_cap: usize) -> bool {
    if buffer.len() >= hard_cap {
        return true;
    }

    let remaining = hard_cap - buffer.len();
    if chunk.len() > remaining {
        buffer.extend_from_slice(&chunk[..remaining]);
        return true;
    }

    buffer.extend_from_slice(chunk);
    buffer.len() >= hard_cap
}

/// Normalizes a list of domain strings: lowercase, strip scheme/path/port, dedup.
fn normalize_domains(domains: Vec<String>) -> Vec<String> {
    let mut normalized = domains
        .into_iter()
        .filter_map(|d| normalize_single_domain(&d))
        .collect::<Vec<_>>();
    normalized.sort_unstable();
    normalized.dedup();
    normalized
}

/// Normalizes a single domain: lowercase, strip scheme, path, port, dots.
fn normalize_single_domain(raw: &str) -> Option<String> {
    let mut d = raw.trim().to_lowercase();
    if d.is_empty() {
        return None;
    }

    if let Some(stripped) = d.strip_prefix("https://") {
        d = stripped.to_string();
    } else if let Some(stripped) = d.strip_prefix("http://") {
        d = stripped.to_string();
    }

    if let Some((host, _)) = d.split_once('/') {
        d = host.to_string();
    }

    d = d.trim_start_matches('.').trim_end_matches('.').to_string();

    if let Some((host, _)) = d.split_once(':') {
        d = host.to_string();
    }

    if d.is_empty() || d.chars().any(char::is_whitespace) {
        return None;
    }

    Some(d)
}

/// Extracts the hostname from a URL (strips scheme, userinfo, port, path).
fn extract_web_fetch_host(url: &str) -> anyhow::Result<String> {
    let rest = url
        .strip_prefix("http://")
        .or_else(|| url.strip_prefix("https://"))
        .ok_or_else(|| anyhow::anyhow!("Only http:// and https:// URLs are allowed"))?;

    let authority = rest
        .split(['/', '?', '#'])
        .next()
        .ok_or_else(|| anyhow::anyhow!("Invalid URL"))?;

    if authority.is_empty() {
        anyhow::bail!("URL must include a host");
    }

    if authority.contains('@') {
        anyhow::bail!("URL userinfo is not allowed");
    }

    if authority.starts_with('[') {
        anyhow::bail!("IPv6 hosts are not supported in web_fetch");
    }

    let host = authority
        .split(':')
        .next()
        .unwrap_or_default()
        .trim()
        .trim_end_matches('.')
        .to_lowercase();

    if host.is_empty() {
        anyhow::bail!("URL must include a valid host");
    }

    Ok(host)
}

/// Checks if a host matches any entry in a domain list (exact or subdomain).
///
/// Supports wildcard `"*"` to match all hosts.
fn host_matches_list(host: &str, domains: &[String]) -> bool {
    if domains.iter().any(|d| d == "*") {
        return true;
    }

    domains.iter().any(|domain| {
        host == domain
            || host
                .strip_suffix(domain)
                .is_some_and(|prefix| prefix.ends_with('.'))
    })
}

/// Returns `true` if the host is localhost, a local TLD, or a private/reserved IP.
fn is_private_or_local_host(host: &str) -> bool {
    let bare = host
        .strip_prefix('[')
        .and_then(|h| h.strip_suffix(']'))
        .unwrap_or(host);

    let has_local_tld = bare
        .rsplit('.')
        .next()
        .is_some_and(|label| label == "local");

    if bare == "localhost" || bare.ends_with(".localhost") || has_local_tld {
        return true;
    }

    if let Ok(ip) = bare.parse::<std::net::IpAddr>() {
        return match ip {
            std::net::IpAddr::V4(v4) => is_non_global_v4(v4),
            std::net::IpAddr::V6(v6) => is_non_global_v6(v6),
        };
    }

    false
}

/// Validates that a hostname resolves to only public (globally routable) IPs.
///
/// Uses [`tokio::net::lookup_host`] for non-blocking DNS resolution, safe
/// to call from async worker threads without starving the executor.
///
/// In test builds this is a no-op to avoid DNS-dependent test flakiness.
#[cfg(not(test))]
async fn validate_resolved_host_is_public(host: &str) -> anyhow::Result<()> {
    let ips: Vec<std::net::IpAddr> = tokio::net::lookup_host((host, 0))
        .await
        .map_err(|e| anyhow::anyhow!("Failed to resolve host '{host}': {e}"))?
        .map(|addr| addr.ip())
        .collect();

    validate_resolved_ips_are_public(host, &ips)
}

/// Test stub: skips DNS resolution to avoid flaky network-dependent tests.
#[cfg(test)]
#[allow(clippy::unnecessary_wraps, clippy::unused_async)]
async fn validate_resolved_host_is_public(_host: &str) -> anyhow::Result<()> {
    Ok(())
}

/// Validates that all resolved IPs are globally routable.
fn validate_resolved_ips_are_public(host: &str, ips: &[std::net::IpAddr]) -> anyhow::Result<()> {
    if ips.is_empty() {
        anyhow::bail!("Failed to resolve host '{host}'");
    }

    for ip in ips {
        let non_global = match ip {
            std::net::IpAddr::V4(v4) => is_non_global_v4(*v4),
            std::net::IpAddr::V6(v6) => is_non_global_v6(*v6),
        };
        if non_global {
            anyhow::bail!("Blocked host '{host}' resolved to non-global address {ip}");
        }
    }

    Ok(())
}

/// Returns `true` for non-globally-routable IPv4 addresses (loopback, private,
/// link-local, multicast, shared address space, test-net, reserved).
fn is_non_global_v4(v4: std::net::Ipv4Addr) -> bool {
    let [a, b, c, _] = v4.octets();
    v4.is_loopback()
        || v4.is_private()
        || v4.is_link_local()
        || v4.is_unspecified()
        || v4.is_broadcast()
        || v4.is_multicast()
        || (a == 100 && (64..=127).contains(&b))  // Shared Address Space (RFC 6598)
        || a >= 240                                 // Reserved (RFC 1112)
        || (a == 192 && b == 0 && (c == 0 || c == 2)) // TEST-NET-1
        || (a == 198 && b == 51)                    // TEST-NET-2
        || (a == 203 && b == 0)                     // TEST-NET-3
        || (a == 198 && (18..=19).contains(&b)) // Benchmarking (RFC 2544)
}

/// Returns `true` for non-globally-routable IPv6 addresses (loopback,
/// unspecified, multicast, unique-local, link-local, documentation,
/// and IPv4-mapped non-global).
fn is_non_global_v6(v6: std::net::Ipv6Addr) -> bool {
    let segs = v6.segments();
    v6.is_loopback()
        || v6.is_unspecified()
        || v6.is_multicast()
        || (segs[0] & 0xfe00) == 0xfc00            // Unique-local (fc00::/7)
        || (segs[0] & 0xffc0) == 0xfe80            // Link-local (fe80::/10)
        || (segs[0] == 0x2001 && segs[1] == 0x0db8) // Documentation (2001:db8::/32)
        || v6.to_ipv4_mapped().is_some_and(is_non_global_v4)
}

/// Builds the tools registry for the Android agent session.
///
/// Constructs tools that are available without upstream's `SecurityPolicy`:
/// - Memory tools (store, recall, forget) via FFI wrappers and upstream
/// - Cron listing tools via upstream constructors
/// - Web search via upstream constructor (when enabled in config)
/// - Web fetch via FFI wrapper (when enabled in config)
///
/// Tools that require `SecurityPolicy` (shell, file I/O, git, browser) are
/// excluded because the upstream security module is `pub(crate)`. These
/// tools are also less relevant on Android where the OS sandbox provides
/// security boundaries.
fn build_tools_registry(config: &zeroclaw::Config, memory: Arc<dyn Memory>) -> Vec<Box<dyn Tool>> {
    let config_arc = Arc::new(config.clone());
    let mut tools: Vec<Box<dyn Tool>> = vec![
        Box::new(FfiMemoryStoreTool {
            memory: memory.clone(),
        }),
        Box::new(zeroclaw::tools::MemoryRecallTool::new(memory.clone())),
        Box::new(FfiMemoryForgetTool { memory }),
        Box::new(zeroclaw::tools::CronListTool::new(config_arc.clone())),
        Box::new(zeroclaw::tools::CronRunsTool::new(config_arc)),
    ];

    if config.web_search.enabled {
        tools.push(Box::new(zeroclaw::tools::WebSearchTool::new(
            config.web_search.provider.clone(),
            config.web_search.brave_api_key.clone(),
            config.web_search.max_results,
            config.web_search.timeout_secs,
        )));
    }

    if config.web_fetch.enabled {
        tools.push(Box::new(FfiWebFetchTool {
            allowed_domains: normalize_domains(config.web_fetch.allowed_domains.clone()),
            blocked_domains: normalize_domains(config.web_fetch.blocked_domains.clone()),
            max_response_size: config.web_fetch.max_response_size,
            timeout_secs: config.web_fetch.timeout_secs,
        }));
    }

    tools
}

/// Generates [`ToolSpec`] metadata from the tools registry.
///
/// Uses each tool's [`Tool::spec`] method to produce the name, description,
/// and JSON parameter schema that the provider uses for native tool calling.
fn tool_specs_from_registry(tools: &[Box<dyn Tool>]) -> Vec<ToolSpec> {
    tools.iter().map(|t| t.spec()).collect()
}

/// Internal session state holding conversation history and provider config.
///
/// Not exposed across the FFI boundary -- Kotlin interacts exclusively
/// through exported free functions and the [`FfiSessionListener`] callback.
struct Session {
    /// Accumulated conversation messages (user + assistant turns).
    history: Vec<ChatMessage>,
    /// Parsed daemon configuration snapshot taken at session creation.
    config: zeroclaw::Config,
    /// Assembled system prompt (identity + workspace files).
    system_prompt: String,
    /// Model identifier passed to the provider (e.g. `"gpt-4o"`).
    model: String,
    /// Sampling temperature for the provider.
    temperature: f64,
    /// Provider name used to create the provider instance (e.g. `"openai"`).
    provider_name: String,
    /// Tools registry built from available upstream tools and FFI wrappers.
    tools_registry: Vec<Box<dyn Tool>>,
}

/// RAII guard that ensures taken-out session state (history + tools) is
/// always restored, even if a panic occurs during processing.
///
/// When [`session_send_inner`] takes history and tools out of the
/// [`SESSION`] mutex for processing, a panic between take and put-back
/// would leave the session in a zombified state (active but empty).
/// This guard's [`Drop`] implementation puts the state back
/// automatically during stack unwinding, preventing permanent data loss.
///
/// Call [`SessionStateGuard::defuse`] after a successful put-back to
/// prevent a redundant restore.
struct SessionStateGuard {
    /// Conversation history taken from the session. `None` once defused.
    history: Option<Vec<ChatMessage>>,
    /// Tools registry taken from the session. `None` once defused.
    tools: Option<Vec<Box<dyn Tool>>>,
}

impl SessionStateGuard {
    /// Creates a new guard holding the taken-out session state.
    fn new(history: Vec<ChatMessage>, tools: Vec<Box<dyn Tool>>) -> Self {
        Self {
            history: Some(history),
            tools: Some(tools),
        }
    }

    /// Returns mutable references to the held history and tools.
    fn state_mut(&mut self) -> (&mut Vec<ChatMessage>, &[Box<dyn Tool>]) {
        (
            self.history.as_mut().expect("guard already defused"),
            self.tools.as_deref().expect("guard already defused"),
        )
    }

    /// Consumes the held state, returning ownership to the caller.
    ///
    /// After this call the guard's [`Drop`] is a no-op.
    fn take(mut self) -> (Vec<ChatMessage>, Vec<Box<dyn Tool>>) {
        (
            self.history.take().expect("guard already defused"),
            self.tools.take().expect("guard already defused"),
        )
    }
}

impl Drop for SessionStateGuard {
    fn drop(&mut self) {
        let Some(history) = self.history.take() else {
            return;
        };
        let Some(tools) = self.tools.take() else {
            return;
        };

        tracing::warn!("SessionStateGuard::drop restoring state after panic");
        let mut guard = lock_session();
        if let Some(session) = guard.as_mut() {
            session.history = history;
            session.tools_registry = tools;
        }
        // Also clear cancel token to prevent stale state.
        *lock_cancel_token() = None;
    }
}

/// A single conversation message exchanged over the FFI boundary.
///
/// Mirrors [`zeroclaw::providers::ChatMessage`] but uses UniFFI-compatible
/// types. The `role` field is one of `"system"`, `"user"`, or `"assistant"`.
#[derive(uniffi::Record, Clone, Debug)]
pub struct SessionMessage {
    /// The message role: `"system"`, `"user"`, or `"assistant"`.
    pub role: String,
    /// The text content of the message.
    pub content: String,
}

/// Callback interface that Kotlin implements to receive live agent session events.
///
/// Events are dispatched from the tokio runtime thread during
/// [`session_send`](crate::session_send). Implementations must be
/// thread-safe (`Send + Sync`). Each callback corresponds to a distinct
/// phase of the agent's tool-call loop execution.
#[uniffi::export(callback_interface)]
pub trait FfiSessionListener: Send + Sync {
    /// The agent is producing internal reasoning (thinking/planning).
    ///
    /// Called with progressive text chunks as the agent reasons about
    /// which tools to invoke or how to answer.
    fn on_thinking(&self, text: String);

    /// A chunk of the agent's final response text has arrived.
    ///
    /// Called incrementally as the provider streams response tokens.
    /// Concatenating all chunks yields the full response.
    fn on_response_chunk(&self, text: String);

    /// The agent is about to invoke a tool.
    ///
    /// `name` is the tool identifier (e.g. `"read_file"`).
    /// `arguments_hint` is a short summary of the arguments, which may
    /// be empty if no hint is available.
    fn on_tool_start(&self, name: String, arguments_hint: String);

    /// A tool invocation has completed.
    ///
    /// `name` is the tool identifier, `success` indicates whether the
    /// tool returned a result or an error, and `duration_secs` is the
    /// wall-clock execution time rounded to whole seconds.
    fn on_tool_result(&self, name: String, success: bool, duration_secs: u64);

    /// Raw tool output text for display in a collapsible detail section.
    ///
    /// Called after [`on_tool_result`](FfiSessionListener::on_tool_result)
    /// with the full stdout/stderr captured from the tool execution.
    fn on_tool_output(&self, name: String, output: String);

    /// A progress status line from the agent loop.
    ///
    /// Used for miscellaneous status updates that do not fit the other
    /// callback categories (e.g. `"Searching memory..."`).
    fn on_progress(&self, message: String);

    /// The conversation history was compacted to fit the context window.
    ///
    /// `summary` contains the AI-generated summary that replaced older
    /// messages. The UI should display this as a fold/expansion point.
    fn on_compaction(&self, summary: String);

    /// The agent loop has finished and the full response is available.
    ///
    /// `full_response` contains the concatenated final answer. This is
    /// always the last callback for a successful send.
    fn on_complete(&self, full_response: String);

    /// An unrecoverable error occurred during the agent loop.
    ///
    /// `error` contains a human-readable description. The session
    /// remains valid and the caller may retry with a new send.
    fn on_error(&self, error: String);

    /// The current send was cancelled by the user.
    ///
    /// The session remains valid; the caller may issue a new send.
    fn on_cancelled(&self);
}

// ── Session lifecycle ───────────────────────────────────────────────────

/// Creates a new live agent session from the running daemon's configuration.
///
/// Mirrors the setup phase of upstream `zeroclaw::agent::run()`:
///
/// 1. Clones the daemon config snapshot.
/// 2. Resolves provider name, model, and temperature.
/// 3. Loads workspace and community skills.
/// 4. Builds tool description metadata for the system prompt.
/// 5. Creates a temporary provider to query native tool support.
/// 6. Builds the full system prompt via
///    [`zeroclaw::channels::build_system_prompt_with_mode`].
/// 7. Seeds the conversation history with the system prompt.
/// 8. Stores the [`Session`] in the global [`SESSION`] mutex.
///
/// Only one session may exist at a time. Calling this while a session is
/// already active returns [`FfiError::StateError`].
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if a session is already active or
/// the daemon is not running, [`FfiError::StateCorrupted`] if the session
/// mutex is poisoned, or [`FfiError::SpawnError`] if provider creation fails.
pub(crate) fn session_start_inner() -> Result<(), FfiError> {
    let config = clone_daemon_config()?;

    let provider_name = config
        .default_provider
        .as_deref()
        .unwrap_or("openrouter")
        .to_string();

    let model = config
        .default_model
        .as_deref()
        .unwrap_or("anthropic/claude-sonnet-4")
        .to_string();

    let temperature = config.default_temperature;

    // Build tools registry from daemon memory + config.
    let tools_registry = if let Ok(mem) = clone_daemon_memory() {
        build_tools_registry(&config, mem)
    } else {
        tracing::warn!("Memory backend unavailable; session tools will be limited");
        Vec::new()
    };

    // Generate tool descriptions from the real tools registry, plus
    // static descriptions for tools the LLM should know about but that
    // cannot be constructed from the FFI crate.
    let mut tool_descs = build_android_tool_descs(&config);
    for tool in &tools_registry {
        let name = tool.name().to_string();
        if !tool_descs.iter().any(|(n, _)| n == &name) {
            tool_descs.push((name, tool.description().to_string()));
        }
    }

    let tool_desc_refs: Vec<(&str, &str)> = tool_descs
        .iter()
        .map(|(name, desc)| (name.as_str(), desc.as_str()))
        .collect();

    let bootstrap_max_chars = if config.agent.compact_context {
        Some(6000)
    } else {
        None
    };

    let native_tools = {
        let provider =
            zeroclaw::providers::create_provider(&provider_name, config.api_key.as_deref())
                .map_err(|e| FfiError::SpawnError {
                    detail: format!("failed to create provider for native-tools check: {e}"),
                })?;
        provider.supports_native_tools()
    };

    // Upstream `skills` module is `pub(crate)`, so we cannot call
    // `load_skills_with_config` or name the `Skill` type directly.
    // Pass an empty slice -- skill prompt injection still works via
    // workspace file scanning inside `build_system_prompt_with_mode`.
    let mut system_prompt = zeroclaw::channels::build_system_prompt_with_mode(
        &config.workspace_dir,
        &model,
        &tool_desc_refs,
        &[],
        Some(&config.identity),
        bootstrap_max_chars,
        native_tools,
        config.skills.prompt_injection_mode,
    );

    // Upstream AIEOS only renders agent identity fields; Android onboarding
    // also stores user_name, timezone, and communication_style inside the
    // identity JSON object. Extract and append them so the model knows who
    // it is talking to.
    append_android_identity_extras(&mut system_prompt, &config.identity);

    let history = vec![ChatMessage::system(&system_prompt)];

    let session = Session {
        history,
        config,
        system_prompt,
        model,
        temperature,
        provider_name,
        tools_registry,
    };

    let mut guard = lock_session();

    if guard.is_some() {
        return Err(FfiError::StateError {
            detail: "a session is already active; destroy it first".into(),
        });
    }

    *guard = Some(session);

    tracing::info!("Live agent session started");
    Ok(())
}

/// Maximum number of images per session send request.
const MAX_SESSION_IMAGES: usize = 5;

/// Sends a message through the live agent session's tool-call loop.
///
/// This is the core function that drives multi-turn agent interaction.
/// The flow is:
///
/// 1. Validate message size (max 1 MiB) and image arrays.
/// 2. Compose multimodal message with `[IMAGE:...]` markers if images
///    are present.
/// 3. Create a [`CancellationToken`] and store it in [`CANCEL_TOKEN`].
/// 4. Take the session's history out of the [`SESSION`] mutex.
/// 5. Build memory context by recalling relevant memories.
/// 6. Enrich the user message with memory context and a timestamp.
/// 7. Create a fresh provider and tools registry.
/// 8. Run the agent loop ([`run_agent_loop`]).
/// 9. On success: run compaction, put history back, fire `on_complete`.
/// 10. On cancel: keep partial history, put history back, fire
///     `on_cancelled`.
/// 11. On error: truncate history to pre-send state, put history back,
///     fire `on_error`.
/// 12. Clear [`CANCEL_TOKEN`].
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] for oversized messages or
/// mismatched image arrays, [`FfiError::StateError`] if no session is
/// active, [`FfiError::StateCorrupted`] if the session mutex is
/// poisoned, or [`FfiError::SpawnError`] if the agent loop or provider
/// creation fails.
#[allow(clippy::too_many_lines)]
pub(crate) fn session_send_inner(
    message: String,
    image_data: Vec<String>,
    mime_types: Vec<String>,
    listener: Arc<dyn FfiSessionListener>,
) -> Result<(), FfiError> {
    // Validate image arrays before composing the message.
    if image_data.len() != mime_types.len() {
        return Err(FfiError::ConfigError {
            detail: format!(
                "image_data length ({}) != mime_types length ({})",
                image_data.len(),
                mime_types.len()
            ),
        });
    }
    if image_data.len() > MAX_SESSION_IMAGES {
        return Err(FfiError::ConfigError {
            detail: format!(
                "too many images ({}, max {MAX_SESSION_IMAGES})",
                image_data.len()
            ),
        });
    }

    // Compose the final message text, embedding image markers if present.
    let message = compose_multimodal_message(&message, &image_data, &mime_types);

    if message.len() > MAX_MESSAGE_BYTES {
        return Err(FfiError::ConfigError {
            detail: format!(
                "message too large ({} bytes, max {MAX_MESSAGE_BYTES})",
                message.len()
            ),
        });
    }

    let cancel_token = CancellationToken::new();
    {
        let mut ct_guard = lock_cancel_token();
        *ct_guard = Some(cancel_token.clone());
    }

    // Snapshot session state while holding the lock briefly.
    // Wrap in a SessionStateGuard so that a panic during processing
    // automatically restores history + tools via Drop.
    let (mut state_guard, config, model, temperature, provider_name, system_prompt) = {
        let mut guard = lock_session();
        let session = guard.as_mut().ok_or_else(|| FfiError::StateError {
            detail: "no active session; call session_start first".into(),
        })?;
        (
            SessionStateGuard::new(
                std::mem::take(&mut session.history),
                std::mem::take(&mut session.tools_registry),
            ),
            session.config.clone(),
            session.model.clone(),
            session.temperature,
            session.provider_name.clone(),
            session.system_prompt.clone(),
        )
    };

    let (history, tools) = state_guard.state_mut();
    let history_len_before = history.len();
    let handle = crate::runtime::get_or_create_runtime()?;

    // Clone the memory backend *before* entering block_on to avoid holding
    // the DAEMON mutex inside the async block, which could deadlock with a
    // concurrent stop_daemon call.
    let daemon_memory = clone_daemon_memory().ok();

    let result: Result<String, AgentLoopOutcome> = handle.block_on(async {
        // Build memory context (best-effort; skip if memory unavailable).
        let mem_context = match daemon_memory {
            Some(ref mem) => {
                listener.on_progress("Searching memory...".into());
                build_memory_context(mem.as_ref(), &message).await
            }
            None => String::new(),
        };

        // Enrich the user message with memory context and timestamp.
        let timestamp = chrono::Utc::now().format("%Y-%m-%d %H:%M UTC");
        let enriched = if mem_context.is_empty() {
            format!("[{timestamp}] {message}")
        } else {
            format!("{mem_context}[{timestamp}] {message}")
        };

        history.push(ChatMessage::user(enriched));

        // Create provider.
        let provider_runtime_options = zeroclaw::providers::ProviderRuntimeOptions {
            auth_profile_override: None,
            provider_api_url: config.api_url.clone(),
            zeroclaw_dir: config.config_path.parent().map(std::path::PathBuf::from),
            secrets_encrypt: config.secrets.encrypt,
            reasoning_enabled: config.runtime.reasoning_enabled,
        };

        let provider = zeroclaw::providers::create_routed_provider_with_options(
            &provider_name,
            config.api_key.as_deref(),
            config.api_url.as_deref(),
            &config.reliability,
            &config.model_routes,
            &model,
            &provider_runtime_options,
        )
        .map_err(|e| AgentLoopOutcome::Error(format!("failed to create provider: {e}")))?;

        // Build tool specs from the real tools registry plus static
        // descriptions for tools the LLM should know about.
        let mut tool_specs = tool_specs_from_registry(tools);
        for spec in build_android_tool_specs(&config) {
            if !tool_specs.iter().any(|s| s.name == spec.name) {
                tool_specs.push(spec);
            }
        }

        // Run the agent loop with real tool execution.
        run_agent_loop(
            provider.as_ref(),
            history,
            tools,
            &tool_specs,
            &model,
            temperature,
            &cancel_token,
            &listener,
        )
        .await
    });

    // Consume the guard (disarms Drop) and put state back explicitly.
    // If we reach this point, no panic occurred, so we handle all
    // three outcomes and restore state ourselves.
    let (mut history, tools) = state_guard.take();

    match result {
        Ok(full_response) => {
            // Run compaction on the history (best-effort).
            if let Ok(true) = handle.block_on(async {
                let provider =
                    zeroclaw::providers::create_provider(&provider_name, config.api_key.as_deref())
                        .ok();
                if let Some(provider) = provider {
                    auto_compact_history(
                        &mut history,
                        provider.as_ref(),
                        &model,
                        DEFAULT_MAX_HISTORY_MESSAGES,
                    )
                    .await
                } else {
                    Ok(false)
                }
            }) {
                // Find the compaction summary (most recent assistant message
                // that starts with "[Compaction summary]").
                if let Some(summary_msg) = history.iter().rev().find(|m| {
                    m.role == "assistant" && m.content.starts_with("[Compaction summary]")
                }) {
                    listener.on_compaction(summary_msg.content.clone());
                }
            }

            put_session_state_back(history, tools, &system_prompt);
            clear_cancel_token();
            listener.on_complete(full_response);
            Ok(())
        }
        Err(AgentLoopOutcome::Cancelled) => {
            put_session_state_back(history, tools, &system_prompt);
            clear_cancel_token();
            listener.on_cancelled();
            Ok(())
        }
        Err(AgentLoopOutcome::Error(msg)) => {
            // Rollback history to pre-send state.
            history.truncate(history_len_before);
            put_session_state_back(history, tools, &system_prompt);
            clear_cancel_token();
            listener.on_error(msg.clone());
            Err(FfiError::SpawnError { detail: msg })
        }
    }
}

/// Injects seed messages into the active session's conversation history.
///
/// Used to restore prior context (e.g. from Room persistence) before the
/// first [`session_send_inner`] call. Messages are appended after the
/// system prompt in the order provided.
///
/// At most [`MAX_SEED_MESSAGES`] entries are accepted. The `role` field of
/// each [`SessionMessage`] must be `"user"` or `"assistant"`; system
/// messages are silently skipped to prevent system prompt corruption.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if no session is active, or
/// [`FfiError::StateCorrupted`] if the session mutex is poisoned.
pub(crate) fn session_seed_inner(messages: Vec<SessionMessage>) -> Result<(), FfiError> {
    let mut guard = lock_session();
    let session = guard.as_mut().ok_or_else(|| FfiError::StateError {
        detail: "no active session; call session_start first".into(),
    })?;

    let capped = if messages.len() > MAX_SEED_MESSAGES {
        tracing::warn!(
            count = messages.len(),
            max = MAX_SEED_MESSAGES,
            "Seed messages capped"
        );
        &messages[..MAX_SEED_MESSAGES]
    } else {
        &messages
    };

    for msg in capped {
        match msg.role.as_str() {
            "user" => session.history.push(ChatMessage::user(&msg.content)),
            "assistant" => session.history.push(ChatMessage::assistant(&msg.content)),
            "tool" => session.history.push(ChatMessage::tool(&msg.content)),
            _ => {
                // Skip system messages to protect the system prompt.
                tracing::debug!(role = %msg.role, "Skipping seed message with reserved role");
            }
        }
    }

    tracing::info!(count = capped.len(), "Seeded session history");
    Ok(())
}

/// Cancels the currently running [`session_send_inner`] call.
///
/// Sets the [`CANCEL_TOKEN`] to cancelled state. The agent loop checks
/// this token between iterations and tool executions, aborting with an
/// [`AgentLoopOutcome::Cancelled`] result. If no send is in progress,
/// this is a no-op.
///
/// # Errors
///
pub(crate) fn session_cancel_inner() {
    let guard = lock_cancel_token();
    if let Some(token) = guard.as_ref() {
        token.cancel();
        tracing::info!("Session send cancelled");
    }
}

/// Clears the active session's conversation history, retaining only the
/// system prompt.
///
/// After this call the session behaves as if freshly started -- the
/// system prompt is preserved but all user/assistant/tool messages are
/// discarded.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if no session is active, or
/// [`FfiError::StateCorrupted`] if the session mutex is poisoned.
pub(crate) fn session_clear_inner() -> Result<(), FfiError> {
    let mut guard = lock_session();
    let session = guard.as_mut().ok_or_else(|| FfiError::StateError {
        detail: "no active session; call session_start first".into(),
    })?;

    let system_prompt = session.system_prompt.clone();
    session.history = vec![ChatMessage::system(&system_prompt)];

    tracing::info!("Session history cleared");
    Ok(())
}

/// Returns the current conversation history as a list of [`SessionMessage`]
/// records suitable for transfer across the FFI boundary.
///
/// The returned list includes the system prompt (role `"system"`) as the
/// first entry, followed by user, assistant, and tool messages in
/// chronological order.
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if no session is active, or
/// [`FfiError::StateCorrupted`] if the session mutex is poisoned.
pub(crate) fn session_history_inner() -> Result<Vec<SessionMessage>, FfiError> {
    let guard = lock_session();
    let session = guard.as_ref().ok_or_else(|| FfiError::StateError {
        detail: "no active session; call session_start first".into(),
    })?;

    let messages = session
        .history
        .iter()
        .map(|m| SessionMessage {
            role: m.role.clone(),
            content: m.content.clone(),
        })
        .collect();

    Ok(messages)
}

/// Destroys the active session and releases all associated resources.
///
/// After this call, a new session may be created with
/// [`session_start_inner`]. Any in-flight [`session_send_inner`] call is
/// cancelled first via the [`CANCEL_TOKEN`].
///
/// # Errors
///
/// Returns [`FfiError::StateError`] if no session is active, or
/// [`FfiError::StateCorrupted`] if the session mutex is poisoned.
pub(crate) fn session_destroy_inner() -> Result<(), FfiError> {
    // Cancel any in-flight send.
    session_cancel_inner();

    let mut guard = lock_session();
    if guard.take().is_none() {
        return Err(FfiError::StateError {
            detail: "no active session to destroy".into(),
        });
    }

    tracing::info!("Live agent session destroyed");
    Ok(())
}

// ── Agent loop ──────────────────────────────────────────────────────────

/// Outcome categories for the agent loop, used internally to distinguish
/// success, cancellation, and errors without mixing them into `FfiError`.
enum AgentLoopOutcome {
    /// The send was cancelled via [`CANCEL_TOKEN`].
    Cancelled,
    /// An unrecoverable error occurred during the loop.
    Error(String),
}

/// Runs the agent tool-call loop until the LLM produces a final text
/// response, the maximum iteration count is reached, or cancellation
/// is signalled.
///
/// For each iteration:
/// 1. Check the cancellation token.
/// 2. Fire `on_thinking` / `on_progress` via the listener.
/// 3. Call `provider.chat(...)` with the current history and tool specs.
/// 4. If no tool calls: stream the final response, append to history, return.
/// 5. If tool calls: execute tools that exist in the registry and report
///    results; tools not in the registry get a fallback "unavailable" message.
///
/// Tools with real implementations (memory, cron, web search) are executed
/// directly. Tools that require upstream's `pub(crate)` `SecurityPolicy`
/// (shell, file I/O, git, browser) are not in the registry and receive
/// an unavailability response so the LLM can answer without them.
///
/// The function returns the full response text on success, or an
/// [`AgentLoopOutcome`] on failure/cancellation.
#[allow(clippy::too_many_arguments, clippy::too_many_lines)]
async fn run_agent_loop(
    provider: &dyn Provider,
    history: &mut Vec<ChatMessage>,
    tools: &[Box<dyn Tool>],
    tool_specs: &[ToolSpec],
    model: &str,
    temperature: f64,
    cancel_token: &CancellationToken,
    listener: &Arc<dyn FfiSessionListener>,
) -> Result<String, AgentLoopOutcome> {
    let use_native_tools = provider.supports_native_tools() && !tool_specs.is_empty();
    let request_tools = if use_native_tools {
        Some(tool_specs)
    } else {
        None
    };

    for iteration in 0..DEFAULT_MAX_TOOL_ITERATIONS {
        // Check cancellation before each iteration.
        if cancel_token.is_cancelled() {
            return Err(AgentLoopOutcome::Cancelled);
        }

        // Progress: thinking.
        let phase = if iteration == 0 {
            "Thinking...".to_string()
        } else {
            format!("Thinking (round {})...", iteration + 1)
        };
        listener.on_thinking(phase);

        // Call the provider.
        let chat_future = provider.chat(
            ChatRequest {
                messages: history,
                tools: request_tools,
            },
            model,
            temperature,
        );

        let chat_result = tokio::select! {
            () = cancel_token.cancelled() => return Err(AgentLoopOutcome::Cancelled),
            result = chat_future => result,
        };

        let response = chat_result
            .map_err(|e| AgentLoopOutcome::Error(format!("provider chat failed: {e}")))?;

        // No tool calls -- this is the final response.
        if response.tool_calls.is_empty() {
            let text = response.text_or_empty().to_string();

            // Stream response in chunks.
            stream_response_text(&text, listener, cancel_token)?;

            history.push(ChatMessage::assistant(&text));
            return Ok(text);
        }

        // Has tool calls -- execute those we have and report unavailable for the rest.
        let tool_call_count = response.tool_calls.len();
        listener.on_progress(format!("Got {tool_call_count} tool call(s)"));

        // Push assistant message with tool calls context.
        let assistant_text = response.text_or_empty().to_string();
        if use_native_tools {
            let native_history = build_native_assistant_history(
                &assistant_text,
                &response.tool_calls,
                response.reasoning_content.as_deref(),
            );
            history.push(ChatMessage::assistant(native_history));
        } else {
            history.push(ChatMessage::assistant(&assistant_text));
        }

        // Execute or respond to each tool call.
        let mut tool_results_text = String::new();

        for call in &response.tool_calls {
            if cancel_token.is_cancelled() {
                return Err(AgentLoopOutcome::Cancelled);
            }

            let args_hint = truncate_tool_args_hint(&call.name, &call.arguments);
            listener.on_tool_start(call.name.clone(), args_hint);

            let start_time = std::time::Instant::now();

            // Find the tool by name in the registry.
            let tool = tools.iter().find(|t| t.name() == call.name);

            let (success, output) = if let Some(tool) = tool {
                let args: serde_json::Value =
                    serde_json::from_str(&call.arguments).unwrap_or(serde_json::json!({}));

                let exec_result = tokio::select! {
                    () = cancel_token.cancelled() => {
                        return Err(AgentLoopOutcome::Cancelled);
                    }
                    result = tool.execute(args) => result,
                };

                match exec_result {
                    Ok(result) => {
                        if result.success {
                            (true, result.output)
                        } else {
                            (
                                false,
                                result
                                    .error
                                    .unwrap_or_else(|| "Tool failed without error message".into()),
                            )
                        }
                    }
                    Err(e) => (false, format!("Tool execution error: {e}")),
                }
            } else {
                tracing::warn!(
                    tool = %call.name,
                    "Tool not in FFI session registry; returning unavailable message"
                );
                (
                    false,
                    format!(
                        "Tool '{}' is not available in this session. \
                         Please answer directly without this tool.",
                        call.name
                    ),
                )
            };

            let duration_secs = start_time.elapsed().as_secs();
            listener.on_tool_result(call.name.clone(), success, duration_secs);
            listener.on_tool_output(call.name.clone(), output.clone());

            if use_native_tools {
                let tool_msg = serde_json::json!({
                    "tool_call_id": call.id,
                    "content": output,
                });
                history.push(ChatMessage::tool(tool_msg.to_string()));
            } else {
                let _ = writeln!(
                    tool_results_text,
                    "<tool_result name=\"{}\">\n{output}\n</tool_result>",
                    call.name
                );
            }
        }

        // For prompt-guided mode, append collected tool results as a user message.
        if !use_native_tools && !tool_results_text.is_empty() {
            history.push(ChatMessage::user(format!(
                "[Tool results]\n{tool_results_text}"
            )));
        }
    }

    Err(AgentLoopOutcome::Error(format!(
        "Agent exceeded maximum tool iterations ({DEFAULT_MAX_TOOL_ITERATIONS})"
    )))
}

// ── Multimodal message composition ──────────────────────────────────────

/// Composes a user message with embedded `[IMAGE:...]` markers.
///
/// When `image_data` is empty the original `text` is returned unchanged.
/// Otherwise each base64-encoded image is appended as an `[IMAGE:data:
/// <mime>;base64,<payload>]` marker. The upstream provider's
/// `to_message_content` parser (in `compatible.rs`) recognises these
/// markers and converts them to multimodal content parts.
fn compose_multimodal_message(text: &str, image_data: &[String], mime_types: &[String]) -> String {
    if image_data.is_empty() {
        return text.to_string();
    }

    let mut buf =
        String::with_capacity(text.len() + image_data.iter().map(String::len).sum::<usize>() + 256);
    buf.push_str(text);

    for (data, mime) in image_data.iter().zip(mime_types.iter()) {
        buf.push_str("\n\n[IMAGE:data:");
        buf.push_str(mime);
        buf.push_str(";base64,");
        buf.push_str(data);
        buf.push(']');
    }

    buf
}

// ── Android identity extras ─────────────────────────────────────────────

/// Appends Android-specific identity fields to the system prompt.
///
/// The upstream AIEOS renderer only outputs agent identity (name, bio,
/// personality). Android onboarding also stores `user_name`, `timezone`,
/// and `communication_style` inside the `identity` JSON object. These
/// fields are silently dropped by serde because they don't exist in the
/// upstream `IdentitySection` struct.
///
/// This function parses the raw `aieos_inline` JSON, extracts those
/// extra fields, and appends a "## User Context" section to the prompt.
fn append_android_identity_extras(
    prompt: &mut String,
    identity_config: &zeroclaw::config::IdentityConfig,
) {
    use std::fmt::Write;

    let Some(ref inline) = identity_config.aieos_inline else {
        return;
    };

    let Ok(root) = serde_json::from_str::<serde_json::Value>(inline) else {
        return;
    };

    let identity_obj = match root.get("identity") {
        Some(v) => v,
        None => &root,
    };

    let user_name = identity_obj
        .get("user_name")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    let timezone = identity_obj
        .get("timezone")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    let comm_style = identity_obj
        .get("communication_style")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    if user_name.is_empty() && timezone.is_empty() && comm_style.is_empty() {
        return;
    }

    prompt.push_str("\n## User Context\n\n");
    if !user_name.is_empty() {
        let _ = writeln!(prompt, "**User's name:** {user_name}");
    }
    if !timezone.is_empty() {
        let _ = writeln!(prompt, "**Timezone:** {timezone}");
    }
    if !comm_style.is_empty() {
        let _ = writeln!(prompt, "**Preferred communication style:** {comm_style}");
    }
}

// ── Memory context ──────────────────────────────────────────────────────

/// Queries the memory backend for entries relevant to the user message
/// and formats them as a context preamble string.
///
/// Mirrors upstream `build_context()` but simplified for the FFI session.
/// Entries whose key matches the assistant autosave pattern are skipped
/// to avoid injecting raw LLM output back as context.
///
/// Returns an empty string if no relevant memories are found or the
/// memory query fails.
async fn build_memory_context(mem: &dyn Memory, query: &str) -> String {
    let Ok(entries) = mem.recall(query, 5, None).await else {
        return String::new();
    };

    // Filter out autosave entries and low-relevance results.
    let relevant: Vec<_> = entries
        .iter()
        .filter(|e| !zeroclaw::memory::is_assistant_autosave_key(&e.key))
        .filter(|e| match e.score {
            Some(score) => score >= 0.3,
            None => true,
        })
        .collect();

    if relevant.is_empty() {
        return String::new();
    }

    let mut context = String::from("[Memory context]\n");
    for entry in &relevant {
        let _ = writeln!(context, "- {}: {}", entry.key, entry.content);
    }
    context.push('\n');

    context
}

// ── Compaction ───────────────────────────────────────────────────────────

/// Automatically compacts conversation history when it exceeds the
/// `max_history` threshold.
///
/// Mirrors upstream `auto_compact_history()`:
/// 1. Counts non-system messages.
/// 2. If count exceeds `max_history`, takes the oldest messages
///    (keeping [`COMPACTION_KEEP_RECENT`] recent ones).
/// 3. Builds a transcript of the compactable messages.
/// 4. Asks the provider to summarise the transcript.
/// 5. Replaces the compacted messages with a single
///    `[Compaction summary]` assistant message.
///
/// Returns `true` if compaction occurred, `false` if history was within
/// limits.
async fn auto_compact_history(
    history: &mut Vec<ChatMessage>,
    provider: &dyn Provider,
    model: &str,
    max_history: usize,
) -> Result<bool, AgentLoopOutcome> {
    let has_system = history.first().is_some_and(|m| m.role == "system");
    let non_system_count = if has_system {
        history.len().saturating_sub(1)
    } else {
        history.len()
    };

    if non_system_count <= max_history {
        return Ok(false);
    }

    let start = usize::from(has_system);
    let keep_recent = COMPACTION_KEEP_RECENT.min(non_system_count);
    let compact_count = non_system_count.saturating_sub(keep_recent);
    if compact_count == 0 {
        return Ok(false);
    }

    let compact_end = start + compact_count;
    let to_compact: Vec<ChatMessage> = history[start..compact_end].to_vec();
    let transcript = build_compaction_transcript(&to_compact);

    let summariser_system = "You are a conversation compaction engine. Summarize older chat \
        history into concise context for future turns. Preserve: user preferences, commitments, \
        decisions, unresolved tasks, key facts. Omit: filler, repeated chit-chat, verbose tool \
        logs. Output plain text bullet points only.";

    let summariser_user = format!(
        "Summarize the following conversation history for context preservation. \
         Keep it short (max 12 bullet points).\n\n{transcript}"
    );

    let summary_raw = provider
        .chat_with_system(Some(summariser_system), &summariser_user, model, 0.2)
        .await
        .unwrap_or_else(|_| {
            // Fallback to deterministic local truncation.
            truncate_chars(&transcript, COMPACTION_MAX_SUMMARY_CHARS)
        });

    let summary = truncate_chars(&summary_raw, COMPACTION_MAX_SUMMARY_CHARS);

    let summary_msg = ChatMessage::assistant(format!("[Compaction summary]\n{}", summary.trim()));
    history.splice(start..compact_end, std::iter::once(summary_msg));

    Ok(true)
}

/// Trims conversation history to prevent unbounded growth.
///
/// Preserves the system prompt (first message if role=system) and the most
/// recent `max_history` non-system messages, draining the oldest entries.
fn trim_history(history: &mut Vec<ChatMessage>, max_history: usize) {
    let has_system = history.first().is_some_and(|m| m.role == "system");
    let non_system_count = if has_system {
        history.len().saturating_sub(1)
    } else {
        history.len()
    };

    if non_system_count <= max_history {
        return;
    }

    let start = usize::from(has_system);
    let to_remove = non_system_count - max_history;
    history.drain(start..start + to_remove);
}

/// Builds a transcript of messages for the compaction summariser.
///
/// Each message is formatted as `"ROLE: content"` on its own line.
/// The output is capped at [`COMPACTION_MAX_SOURCE_CHARS`] characters.
fn build_compaction_transcript(messages: &[ChatMessage]) -> String {
    let mut transcript = String::new();
    for msg in messages {
        let role = msg.role.to_uppercase();
        let _ = writeln!(transcript, "{role}: {}", msg.content.trim());
    }

    if transcript.chars().count() > COMPACTION_MAX_SOURCE_CHARS {
        truncate_chars(&transcript, COMPACTION_MAX_SOURCE_CHARS)
    } else {
        transcript
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

/// Truncates a string to `max_chars` characters, appending `"..."` if truncated.
fn truncate_chars(s: &str, max_chars: usize) -> String {
    match s.char_indices().nth(max_chars) {
        Some((idx, _)) => {
            let truncated = &s[..idx];
            format!("{}...", truncated.trim_end())
        }
        None => s.to_string(),
    }
}

/// Builds tool specifications for the Android-appropriate tool set.
///
/// These specs are passed to `provider.chat()` so the LLM is aware of
/// available tools. Because upstream's `SecurityPolicy` is `pub(crate)`,
/// we cannot instantiate actual tool objects; these specs serve only as
/// metadata for the provider's native tool calling protocol.
fn build_android_tool_specs(config: &zeroclaw::Config) -> Vec<ToolSpec> {
    let descs = build_android_tool_descs(config);
    descs
        .into_iter()
        .map(|(name, description)| ToolSpec {
            name,
            description,
            parameters: serde_json::json!({
                "type": "object",
                "properties": {},
            }),
        })
        .collect()
}

/// Extracts a short hint from tool call arguments for the progress display.
///
/// For `shell` tools, shows the `command` field. For file tools, shows the
/// `path` field. For other tools, shows the `action` or `query` field.
fn truncate_tool_args_hint(tool_name: &str, arguments_json: &str) -> String {
    let args: serde_json::Value =
        serde_json::from_str(arguments_json).unwrap_or(serde_json::json!({}));

    let hint = match tool_name {
        "shell" => args.get("command").and_then(|v| v.as_str()),
        "file_read" | "file_write" => args.get("path").and_then(|v| v.as_str()),
        _ => args
            .get("action")
            .and_then(|v| v.as_str())
            .or_else(|| args.get("query").and_then(|v| v.as_str())),
    };

    match hint {
        Some(s) => truncate_chars(s, 60),
        None => String::new(),
    }
}

/// Streams the final response text to the listener in chunks of at least
/// [`STREAM_CHUNK_MIN_CHARS`] characters, split on whitespace boundaries.
fn stream_response_text(
    text: &str,
    listener: &Arc<dyn FfiSessionListener>,
    cancel_token: &CancellationToken,
) -> Result<(), AgentLoopOutcome> {
    let mut chunk = String::new();
    for word in text.split_inclusive(char::is_whitespace) {
        if cancel_token.is_cancelled() {
            return Err(AgentLoopOutcome::Cancelled);
        }
        chunk.push_str(word);
        if chunk.len() >= STREAM_CHUNK_MIN_CHARS {
            listener.on_response_chunk(std::mem::take(&mut chunk));
        }
    }
    if !chunk.is_empty() {
        listener.on_response_chunk(chunk);
    }
    Ok(())
}

/// Builds a JSON-structured assistant history entry for native tool calling mode.
///
/// Preserves tool call IDs so subsequent `role=tool` messages can reference
/// the correct call. Also preserves `reasoning_content` from thinking models.
fn build_native_assistant_history(
    text: &str,
    tool_calls: &[zeroclaw::providers::ToolCall],
    reasoning_content: Option<&str>,
) -> String {
    let calls_json: Vec<serde_json::Value> = tool_calls
        .iter()
        .map(|tc| {
            serde_json::json!({
                "id": tc.id,
                "type": "function",
                "function": {
                    "name": tc.name,
                    "arguments": tc.arguments,
                },
            })
        })
        .collect();

    let mut msg = serde_json::json!({
        "content": text,
        "tool_calls": calls_json,
    });

    if let Some(rc) = reasoning_content {
        msg["reasoning_content"] = serde_json::Value::String(rc.to_string());
    }

    msg.to_string()
}

/// Puts the working history and tools registry back into the [`SESSION`] mutex.
///
/// If the session was destroyed while the send was in progress, the
/// state is silently dropped (the session slot will be `None`).
fn put_session_state_back(
    history: Vec<ChatMessage>,
    tools: Vec<Box<dyn Tool>>,
    _system_prompt: &str,
) {
    let mut guard = lock_session();
    if let Some(session) = guard.as_mut() {
        session.history = history;
        session.tools_registry = tools;
    }
}

/// Clears the global [`CANCEL_TOKEN`].
fn clear_cancel_token() {
    *lock_cancel_token() = None;
}

/// Builds the Android-appropriate tool description list for the system prompt.
///
/// Returns `(tool_name, description)` pairs matching the subset of tools
/// available on Android. Hardware peripherals, composio, and screenshot
/// tools are excluded because they require desktop-only capabilities.
///
/// Conditional tools (`browser_open`, `delegate`) are included only when
/// their corresponding config sections are enabled/non-empty.
fn build_android_tool_descs(config: &zeroclaw::Config) -> Vec<(String, String)> {
    let mut descs: Vec<(String, String)> = vec![
        (
            "shell".into(),
            "Execute terminal commands. Use when: running local checks, \
             build/test commands, diagnostics. Don't use when: a safer \
             dedicated tool exists, or command is destructive without approval."
                .into(),
        ),
        (
            "file_read".into(),
            "Read file contents. Use when: inspecting project files, \
             configs, logs. Don't use when: a targeted search is enough."
                .into(),
        ),
        (
            "file_write".into(),
            "Write file contents. Use when: applying focused edits, \
             scaffolding files, updating docs/code. Don't use when: \
             side effects are unclear or file ownership is uncertain."
                .into(),
        ),
        (
            "memory_store".into(),
            "Save to memory. Use when: preserving durable preferences, \
             decisions, key context. Don't use when: information is \
             transient/noisy/sensitive without need."
                .into(),
        ),
        (
            "memory_recall".into(),
            "Search memory. Use when: retrieving prior decisions, user \
             preferences, historical context. Don't use when: answer \
             is already in current context."
                .into(),
        ),
        (
            "memory_forget".into(),
            "Delete a memory entry. Use when: memory is incorrect/stale \
             or explicitly requested for removal. Don't use when: \
             impact is uncertain."
                .into(),
        ),
        (
            "cron_add".into(),
            "Create a cron job. Supports schedule kinds: cron, at, every; \
             and job types: shell or agent."
                .into(),
        ),
        (
            "cron_list".into(),
            "List all cron jobs with schedule, status, and metadata.".into(),
        ),
        ("cron_remove".into(), "Remove a cron job by job_id.".into()),
    ];

    if config.browser.enabled {
        descs.push((
            "browser_open".into(),
            "Open approved HTTPS URLs in system browser \
             (allowlist-only, no scraping)"
                .into(),
        ));
    }

    if config.web_search.enabled {
        descs.push((
            "web_search".into(),
            "Search the web using a search engine. Returns a list of \
             results with titles, URLs, and snippets. Use when: answering \
             questions about current events, looking up facts, finding \
             documentation."
                .into(),
        ));
    }

    if config.web_fetch.enabled {
        descs.push((
            "web_fetch".into(),
            "Fetch a web page and return its content as clean plain text. \
             HTML pages are automatically converted to readable text. \
             JSON and plain text responses are returned as-is. \
             Only GET requests; follows redirects."
                .into(),
        ));
    }

    if !config.agents.is_empty() {
        descs.push((
            "delegate".into(),
            "Delegate a sub-task to a specialized agent. Use when: task \
             needs different model/capability, or to parallelize work."
                .into(),
        ));
    }

    descs
}

// ── Delta string parser ─────────────────────────────────────────────────
//
// Upstream `ZeroClaw`'s `run_tool_call_loop()` emits progress as plain
// strings with emoji prefixes. The parser below converts these strings
// into typed [`FfiSessionListener`] callbacks.

/// Sentinel value emitted by upstream to signal the transition from
/// tool-call progress lines to streamed response tokens.
///
/// After this sentinel, all subsequent deltas are response content
/// until the loop iteration ends.
const DRAFT_CLEAR_SENTINEL: &str = "\x00CLEAR\x00";

/// Dispatches a single progress delta string to the appropriate listener callback.
///
/// The upstream agent loop emits deltas in two phases:
///
/// 1. **Progress phase** -- emoji-prefixed status lines describing thinking,
///    tool starts, tool completions, and other progress.
/// 2. **Response phase** -- raw text chunks of the assistant's streamed reply,
///    entered after [`DRAFT_CLEAR_SENTINEL`] is received.
///
/// `streaming_response` tracks which phase we are in and is mutated when
/// the sentinel is encountered.
pub(crate) fn dispatch_delta(
    delta: &str,
    listener: &dyn FfiSessionListener,
    streaming_response: &mut bool,
) {
    if delta == DRAFT_CLEAR_SENTINEL {
        *streaming_response = true;
        return;
    }

    if *streaming_response {
        listener.on_response_chunk(delta.to_string());
        return;
    }

    let trimmed = delta.trim_end_matches('\n');
    if trimmed.is_empty() {
        return;
    }

    let mut chars = trimmed.chars();
    if let Some(first) = chars.next() {
        let rest = chars.as_str();
        match first {
            '\u{1f914}' => {
                // Thinking / planning
                listener.on_thinking(rest.trim().to_string());
            }
            '\u{23f3}' => {
                // Tool start -- format: "tool_name: hint text"
                let rest = rest.trim();
                let (name, hint) = match rest.find(':') {
                    Some(pos) => (rest[..pos].trim(), rest[pos + 1..].trim()),
                    None => (rest, ""),
                };
                listener.on_tool_start(name.to_string(), hint.to_string());
            }
            '\u{2705}' => {
                // Tool success -- format: "tool_name (3s)"
                let (name, secs) = parse_tool_completion(rest.trim());
                listener.on_tool_result(name, true, secs);
            }
            '\u{274c}' => {
                // Tool failure -- format: "tool_name (2s)"
                let (name, secs) = parse_tool_completion(rest.trim());
                listener.on_tool_result(name, false, secs);
            }
            '\u{1f4ac}' => {
                // Informational progress
                listener.on_progress(rest.trim().to_string());
            }
            _ => {
                // Unrecognised prefix -- treat as generic progress
                listener.on_progress(trimmed.to_string());
            }
        }
    }
}

/// Parses a tool completion string into `(tool_name, duration_seconds)`.
///
/// Expected format: `"tool_name (Ns)"` where `N` is an integer.
/// If no parenthesised duration is found, returns `(input, 0)`.
///
/// # Examples
///
/// ```text
/// "read_file (3s)" -> ("read_file", 3)
/// "read_file"      -> ("read_file", 0)
/// ```
fn parse_tool_completion(s: &str) -> (String, u64) {
    if let Some(paren_start) = s.rfind('(') {
        let name = s[..paren_start].trim();
        let inside = &s[paren_start + 1..];
        let secs = inside
            .trim_end_matches(')')
            .trim()
            .trim_end_matches('s')
            .trim()
            .parse::<u64>()
            .unwrap_or(0);
        (name.to_string(), secs)
    } else {
        (s.to_string(), 0)
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;
    use std::sync::Mutex as StdMutex;

    /// A test listener that records all callback invocations as strings.
    ///
    /// Each event is formatted as `"callback_name:payload"` and pushed
    /// onto the internal vector for later assertion.
    struct RecordingListener {
        /// Accumulated event strings.
        events: StdMutex<Vec<String>>,
    }

    impl RecordingListener {
        /// Creates a new empty recording listener.
        fn new() -> Self {
            Self {
                events: StdMutex::new(Vec::new()),
            }
        }

        /// Returns a snapshot of all recorded events.
        fn events(&self) -> Vec<String> {
            self.events.lock().unwrap().clone()
        }
    }

    impl FfiSessionListener for RecordingListener {
        fn on_thinking(&self, text: String) {
            self.events.lock().unwrap().push(format!("thinking:{text}"));
        }

        fn on_response_chunk(&self, text: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("response_chunk:{text}"));
        }

        fn on_tool_start(&self, name: String, arguments_hint: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_start:{name}:{arguments_hint}"));
        }

        fn on_tool_result(&self, name: String, success: bool, duration_secs: u64) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_result:{name}:{success}:{duration_secs}"));
        }

        fn on_tool_output(&self, name: String, output: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("tool_output:{name}:{output}"));
        }

        fn on_progress(&self, message: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("progress:{message}"));
        }

        fn on_compaction(&self, summary: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("compaction:{summary}"));
        }

        fn on_complete(&self, full_response: String) {
            self.events
                .lock()
                .unwrap()
                .push(format!("complete:{full_response}"));
        }

        fn on_error(&self, error: String) {
            self.events.lock().unwrap().push(format!("error:{error}"));
        }

        fn on_cancelled(&self) {
            self.events.lock().unwrap().push("cancelled".to_string());
        }
    }

    // ── dispatch_delta tests ────────────────────────────────────────

    #[test]
    fn test_dispatch_thinking_first_round() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{1f914} Planning next steps\n", &listener, &mut streaming);
        assert!(!streaming);
        assert_eq!(listener.events(), vec!["thinking:Planning next steps"]);
    }

    #[test]
    fn test_dispatch_thinking_round_n() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{1f914} Re-evaluating approach\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(listener.events(), vec!["thinking:Re-evaluating approach"]);
    }

    #[test]
    fn test_dispatch_tool_start_with_hint() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{23f3} read_file: /src/main.rs\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(listener.events(), vec!["tool_start:read_file:/src/main.rs"]);
    }

    #[test]
    fn test_dispatch_tool_start_no_hint() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{23f3} list_files\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["tool_start:list_files:"]);
    }

    #[test]
    fn test_dispatch_tool_success() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{2705} read_file (3s)\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["tool_result:read_file:true:3"]);
    }

    #[test]
    fn test_dispatch_tool_failure() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(
            "\u{274c} execute_command (12s)\n",
            &listener,
            &mut streaming,
        );
        assert_eq!(
            listener.events(),
            vec!["tool_result:execute_command:false:12"]
        );
    }

    #[test]
    fn test_dispatch_got_tool_calls() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta("\u{1f4ac} Got 3 tool calls\n", &listener, &mut streaming);
        assert_eq!(listener.events(), vec!["progress:Got 3 tool calls"]);
    }

    #[test]
    fn test_dispatch_sentinel_switches_to_response() {
        let listener = RecordingListener::new();
        let mut streaming = false;
        dispatch_delta(DRAFT_CLEAR_SENTINEL, &listener, &mut streaming);
        assert!(streaming);
        assert!(listener.events().is_empty());
    }

    #[test]
    fn test_dispatch_response_chunks_after_sentinel() {
        let listener = RecordingListener::new();
        let mut streaming = false;

        dispatch_delta(DRAFT_CLEAR_SENTINEL, &listener, &mut streaming);
        assert!(streaming);

        dispatch_delta("Hello, ", &listener, &mut streaming);
        dispatch_delta("world!", &listener, &mut streaming);

        assert_eq!(
            listener.events(),
            vec!["response_chunk:Hello, ", "response_chunk:world!",]
        );
    }

    // ── parse_tool_completion tests ─────────────────────────────────

    #[test]
    fn test_parse_tool_completion_with_seconds() {
        let (name, secs) = parse_tool_completion("read_file (3s)");
        assert_eq!(name, "read_file");
        assert_eq!(secs, 3);
    }

    #[test]
    fn test_parse_tool_completion_no_parens() {
        let (name, secs) = parse_tool_completion("list_files");
        assert_eq!(name, "list_files");
        assert_eq!(secs, 0);
    }

    // ── truncate_chars tests ────────────────────────────────────────

    #[test]
    fn test_truncate_chars_short_string() {
        let result = truncate_chars("hello", 10);
        assert_eq!(result, "hello");
    }

    #[test]
    fn test_truncate_chars_long_string() {
        let input = "a".repeat(100);
        let result = truncate_chars(&input, 10);
        assert!(result.ends_with("..."));
        assert!(result.len() <= 14); // 10 chars + "..."
    }

    // ── trim_history tests ──────────────────────────────────────────

    #[test]
    fn test_trim_history_within_limit() {
        let mut history = vec![
            ChatMessage::system("system"),
            ChatMessage::user("hello"),
            ChatMessage::assistant("hi"),
        ];
        trim_history(&mut history, 10);
        assert_eq!(history.len(), 3);
    }

    #[test]
    fn test_trim_history_exceeds_limit() {
        let mut history = vec![ChatMessage::system("system")];
        for i in 0..10 {
            history.push(ChatMessage::user(format!("msg {i}")));
        }
        assert_eq!(history.len(), 11); // 1 system + 10 user

        trim_history(&mut history, 5);
        assert_eq!(history.len(), 6); // 1 system + 5 user
        assert_eq!(history[0].role, "system");
        assert_eq!(history[1].content, "msg 5");
    }

    #[test]
    fn test_trim_history_no_system_prompt() {
        let mut history: Vec<ChatMessage> = (0..10)
            .map(|i| ChatMessage::user(format!("msg {i}")))
            .collect();

        trim_history(&mut history, 3);
        assert_eq!(history.len(), 3);
        assert_eq!(history[0].content, "msg 7");
    }

    // ── build_compaction_transcript tests ────────────────────────────

    #[test]
    fn test_build_compaction_transcript_basic() {
        let messages = vec![
            ChatMessage::user("What is Rust?"),
            ChatMessage::assistant("Rust is a systems programming language."),
        ];
        let transcript = build_compaction_transcript(&messages);
        assert!(transcript.contains("USER: What is Rust?"));
        assert!(transcript.contains("ASSISTANT: Rust is a systems programming language."));
    }

    // ── truncate_tool_args_hint tests ───────────────────────────────

    #[test]
    fn test_truncate_tool_args_hint_shell() {
        let hint = truncate_tool_args_hint("shell", r#"{"command":"ls -la"}"#);
        assert_eq!(hint, "ls -la");
    }

    #[test]
    fn test_truncate_tool_args_hint_file_read() {
        let hint = truncate_tool_args_hint("file_read", r#"{"path":"/etc/hosts"}"#);
        assert_eq!(hint, "/etc/hosts");
    }

    #[test]
    fn test_truncate_tool_args_hint_unknown_tool() {
        let hint = truncate_tool_args_hint("unknown", r#"{"query":"search term"}"#);
        assert_eq!(hint, "search term");
    }

    #[test]
    fn test_truncate_tool_args_hint_invalid_json() {
        let hint = truncate_tool_args_hint("shell", "not json");
        assert!(hint.is_empty());
    }

    // ── build_native_assistant_history tests ─────────────────────────

    #[test]
    fn test_build_native_assistant_history_basic() {
        let calls = vec![zeroclaw::providers::ToolCall {
            id: "call_123".into(),
            name: "shell".into(),
            arguments: r#"{"command":"ls"}"#.into(),
        }];

        let result = build_native_assistant_history("Let me check", &calls, None);
        let parsed: serde_json::Value = serde_json::from_str(&result).unwrap();

        assert_eq!(parsed["content"], "Let me check");
        assert_eq!(parsed["tool_calls"][0]["id"], "call_123");
        assert_eq!(parsed["tool_calls"][0]["function"]["name"], "shell");
        assert!(parsed.get("reasoning_content").is_none());
    }

    #[test]
    fn test_build_native_assistant_history_with_reasoning() {
        let calls = vec![zeroclaw::providers::ToolCall {
            id: "call_456".into(),
            name: "file_read".into(),
            arguments: r#"{"path":"test.rs"}"#.into(),
        }];

        let result =
            build_native_assistant_history("Reading file", &calls, Some("thinking about it"));
        let parsed: serde_json::Value = serde_json::from_str(&result).unwrap();

        assert_eq!(parsed["reasoning_content"], "thinking about it");
    }

    // ── stream_response_text tests ──────────────────────────────────

    #[test]
    fn test_stream_response_text_short() {
        let recording = Arc::new(RecordingListener::new());
        let listener: Arc<dyn FfiSessionListener> = recording.clone();
        let token = CancellationToken::new();

        let result = stream_response_text("Hello world", &listener, &token);
        assert!(result.is_ok());

        let events = recording.events();
        assert_eq!(events.len(), 1);
        assert_eq!(events[0], "response_chunk:Hello world");
    }

    #[test]
    fn test_stream_response_text_cancelled() {
        let recording = Arc::new(RecordingListener::new());
        let listener: Arc<dyn FfiSessionListener> = recording.clone();
        let token = CancellationToken::new();
        token.cancel();

        let result = stream_response_text("Hello world", &listener, &token);
        assert!(result.is_err());
    }

    // ── session lifecycle unit tests (no daemon) ────────────────────

    #[test]
    fn test_session_send_no_session() {
        let listener = Arc::new(RecordingListener::new());
        let result = session_send_inner("hello".into(), vec![], vec![], listener);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("no active session"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_session_send_oversized_message() {
        let listener = Arc::new(RecordingListener::new());
        let big_message = "x".repeat(MAX_MESSAGE_BYTES + 1);
        let result = session_send_inner(big_message, vec![], vec![], listener);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("too large"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_session_send_mismatched_image_arrays() {
        let listener = Arc::new(RecordingListener::new());
        let result = session_send_inner("hi".into(), vec!["base64data".into()], vec![], listener);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("image_data length"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_session_send_too_many_images() {
        let listener = Arc::new(RecordingListener::new());
        let images = vec!["img".to_string(); MAX_SESSION_IMAGES + 1];
        let mimes = vec!["image/png".to_string(); MAX_SESSION_IMAGES + 1];
        let result = session_send_inner("hi".into(), images, mimes, listener);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("too many images"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_compose_multimodal_message_no_images() {
        let result = compose_multimodal_message("hello world", &[], &[]);
        assert_eq!(result, "hello world");
    }

    #[test]
    fn test_compose_multimodal_message_with_images() {
        let result =
            compose_multimodal_message("describe this", &["abc123".into()], &["image/png".into()]);
        assert!(result.starts_with("describe this"));
        assert!(result.contains("[IMAGE:data:image/png;base64,abc123]"));
    }

    #[test]
    fn test_session_cancel_no_send() {
        session_cancel_inner();
    }

    #[test]
    fn test_session_clear_no_session() {
        let result = session_clear_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("no active session"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_session_history_no_session() {
        let result = session_history_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("no active session"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    #[test]
    fn test_session_destroy_no_session() {
        let result = session_destroy_inner();
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::StateError { detail } => {
                assert!(detail.contains("no active session"));
            }
            other => panic!("expected StateError, got {other:?}"),
        }
    }

    // ── append_android_identity_extras tests ────────────────────────

    #[test]
    fn test_android_identity_extras_user_name() {
        let config = zeroclaw::config::IdentityConfig {
            format: "aieos".into(),
            aieos_path: None,
            aieos_inline: Some(
                r#"{"identity":{"names":{"first":"Nova"},"user_name":"Alice","timezone":"US/Eastern","communication_style":"casual"}}"#.into(),
            ),
        };
        let mut prompt = String::from("## Identity\n\n**Name:** Nova\n");
        append_android_identity_extras(&mut prompt, &config);
        assert!(prompt.contains("**User's name:** Alice"));
        assert!(prompt.contains("**Timezone:** US/Eastern"));
        assert!(prompt.contains("**Preferred communication style:** casual"));
    }

    #[test]
    fn test_android_identity_extras_empty_inline() {
        let config = zeroclaw::config::IdentityConfig {
            format: "aieos".into(),
            aieos_path: None,
            aieos_inline: None,
        };
        let mut prompt = String::from("base prompt");
        append_android_identity_extras(&mut prompt, &config);
        assert_eq!(prompt, "base prompt");
    }

    #[test]
    fn test_android_identity_extras_no_extra_fields() {
        let config = zeroclaw::config::IdentityConfig {
            format: "aieos".into(),
            aieos_path: None,
            aieos_inline: Some(r#"{"identity":{"names":{"first":"Nova"}}}"#.into()),
        };
        let mut prompt = String::from("base prompt");
        append_android_identity_extras(&mut prompt, &config);
        assert_eq!(prompt, "base prompt");
    }

    // ── SessionStateGuard tests ────────────────────────────────────

    #[test]
    fn test_guard_take_disarms_drop() {
        let history = vec![ChatMessage::user("hello")];
        let guard = SessionStateGuard::new(history, vec![]);

        let (h, t) = guard.take();
        assert_eq!(h.len(), 1);
        assert!(t.is_empty());
        // Drop runs here but is a no-op (defused).
    }

    #[test]
    fn test_guard_state_mut_provides_references() {
        let history = vec![ChatMessage::user("one")];
        let mut guard = SessionStateGuard::new(history, vec![]);

        let (h, _t) = guard.state_mut();
        h.push(ChatMessage::assistant("two"));
        assert_eq!(h.len(), 2);

        let (taken_h, _) = guard.take();
        assert_eq!(taken_h.len(), 2);
        assert_eq!(taken_h[1].content, "two");
    }

    #[test]
    fn test_guard_drop_without_take_keeps_state() {
        // Verify that dropping a guard without calling take() does NOT
        // consume the state (it's available for the Drop impl to use).
        // The actual SESSION restoration is tested implicitly through
        // session_send_inner's panic-safety.
        let history = vec![ChatMessage::user("preserved")];
        let guard = SessionStateGuard::new(history, vec![]);
        // Drop fires here — without a live SESSION it's a no-op,
        // but critically it does NOT panic.
        drop(guard);
    }

    // ── FfiWebFetchTool URL validation tests ────────────────────────────

    fn test_web_fetch_tool(allowed: Vec<&str>) -> FfiWebFetchTool {
        test_web_fetch_tool_with_blocklist(allowed, vec![])
    }

    fn test_web_fetch_tool_with_blocklist(
        allowed: Vec<&str>,
        blocked: Vec<&str>,
    ) -> FfiWebFetchTool {
        FfiWebFetchTool {
            allowed_domains: normalize_domains(allowed.into_iter().map(String::from).collect()),
            blocked_domains: normalize_domains(blocked.into_iter().map(String::from).collect()),
            max_response_size: 500_000,
            timeout_secs: 30,
        }
    }

    #[test]
    fn web_fetch_name_is_correct() {
        let tool = test_web_fetch_tool(vec!["example.com"]);
        assert_eq!(tool.name(), "web_fetch");
    }

    #[test]
    fn web_fetch_schema_requires_url() {
        let tool = test_web_fetch_tool(vec!["example.com"]);
        let schema = tool.parameters_schema();
        assert!(schema["properties"]["url"].is_object());
        let required = schema["required"].as_array().unwrap();
        assert!(required.iter().any(|v| v.as_str() == Some("url")));
    }

    #[test]
    fn web_fetch_validate_accepts_exact_domain() {
        let allowed = vec!["example.com".to_string()];
        let got = validate_web_fetch_url("https://example.com/page", &allowed, &[]).unwrap();
        assert_eq!(got, "https://example.com/page");
    }

    #[test]
    fn web_fetch_validate_accepts_subdomain() {
        let allowed = vec!["example.com".to_string()];
        assert!(validate_web_fetch_url("https://docs.example.com/guide", &allowed, &[]).is_ok());
    }

    #[test]
    fn web_fetch_validate_accepts_wildcard() {
        let allowed = vec!["*".to_string()];
        assert!(validate_web_fetch_url("https://news.ycombinator.com", &allowed, &[]).is_ok());
    }

    #[test]
    fn web_fetch_validate_rejects_empty_url() {
        let allowed = vec!["example.com".to_string()];
        let err = validate_web_fetch_url("", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("empty"));
    }

    #[test]
    fn web_fetch_validate_rejects_ftp_scheme() {
        let allowed = vec!["example.com".to_string()];
        let err = validate_web_fetch_url("ftp://example.com", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("http://") || err.contains("https://"));
    }

    #[test]
    fn web_fetch_validate_rejects_allowlist_miss() {
        let allowed = vec!["example.com".to_string()];
        let err = validate_web_fetch_url("https://google.com", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("allowed_domains"));
    }

    #[test]
    fn web_fetch_validate_requires_allowlist() {
        let err = validate_web_fetch_url("https://example.com", &[], &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("allowed_domains"));
    }

    // ── SSRF protection ─────────────────────────────────────────────────

    #[test]
    fn web_fetch_ssrf_blocks_localhost() {
        let allowed = vec!["localhost".to_string()];
        let err = validate_web_fetch_url("https://localhost:8080", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("local/private"));
    }

    #[test]
    fn web_fetch_ssrf_blocks_private_ipv4() {
        let allowed = vec!["192.168.1.5".to_string()];
        let err = validate_web_fetch_url("https://192.168.1.5", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("local/private"));
    }

    #[test]
    fn web_fetch_ssrf_blocks_loopback() {
        assert!(is_private_or_local_host("127.0.0.1"));
        assert!(is_private_or_local_host("127.0.0.2"));
    }

    #[test]
    fn web_fetch_ssrf_blocks_rfc1918() {
        assert!(is_private_or_local_host("10.0.0.1"));
        assert!(is_private_or_local_host("172.16.0.1"));
        assert!(is_private_or_local_host("192.168.1.1"));
    }

    #[test]
    fn web_fetch_ssrf_wildcard_still_blocks_private() {
        let allowed = vec!["*".to_string()];
        let err = validate_web_fetch_url("https://localhost:8080", &allowed, &[])
            .unwrap_err()
            .to_string();
        assert!(err.contains("local/private"));
    }

    // ── Blocked domains ─────────────────────────────────────────────────

    #[test]
    fn web_fetch_blocklist_rejects_exact() {
        let allowed = vec!["*".to_string()];
        let blocked = vec!["evil.com".to_string()];
        let err = validate_web_fetch_url("https://evil.com/page", &allowed, &blocked)
            .unwrap_err()
            .to_string();
        assert!(err.contains("blocked_domains"));
    }

    #[test]
    fn web_fetch_blocklist_rejects_subdomain() {
        let allowed = vec!["*".to_string()];
        let blocked = vec!["evil.com".to_string()];
        let err = validate_web_fetch_url("https://api.evil.com/v1", &allowed, &blocked)
            .unwrap_err()
            .to_string();
        assert!(err.contains("blocked_domains"));
    }

    #[test]
    fn web_fetch_blocklist_wins_over_allowlist() {
        let allowed = vec!["evil.com".to_string()];
        let blocked = vec!["evil.com".to_string()];
        let err = validate_web_fetch_url("https://evil.com", &allowed, &blocked)
            .unwrap_err()
            .to_string();
        assert!(err.contains("blocked_domains"));
    }

    #[test]
    fn web_fetch_blocklist_allows_non_blocked() {
        let allowed = vec!["*".to_string()];
        let blocked = vec!["evil.com".to_string()];
        assert!(validate_web_fetch_url("https://example.com", &allowed, &blocked).is_ok());
    }

    // ── Domain normalization ────────────────────────────────────────────

    #[test]
    fn web_fetch_normalize_domain_strips_scheme_and_case() {
        let got = normalize_single_domain("  HTTPS://Docs.Example.com/path ").unwrap();
        assert_eq!(got, "docs.example.com");
    }

    #[test]
    fn web_fetch_normalize_deduplicates() {
        let got = normalize_domains(vec![
            "example.com".into(),
            "EXAMPLE.COM".into(),
            "https://example.com/".into(),
        ]);
        assert_eq!(got, vec!["example.com".to_string()]);
    }

    // ── Response truncation ─────────────────────────────────────────────

    #[test]
    fn web_fetch_truncate_within_limit() {
        let tool = test_web_fetch_tool(vec!["example.com"]);
        let text = "hello world";
        assert_eq!(tool.truncate_response(text), "hello world");
    }

    #[test]
    fn web_fetch_truncate_over_limit() {
        let tool = FfiWebFetchTool {
            allowed_domains: vec!["example.com".into()],
            blocked_domains: vec![],
            max_response_size: 10,
            timeout_secs: 30,
        };
        let text = "hello world this is long";
        let truncated = tool.truncate_response(text);
        assert!(truncated.contains("[Response truncated"));
    }

    // ── HTML to text ────────────────────────────────────────────────────

    #[test]
    fn web_fetch_html_to_text() {
        let html = "<html><body><h1>Title</h1><p>Hello <b>world</b></p></body></html>";
        let text = nanohtml2text::html2text(html);
        assert!(text.contains("Title"));
        assert!(text.contains("Hello"));
        assert!(text.contains("world"));
        assert!(!text.contains("<h1>"));
    }

    // ── IP classification ───────────────────────────────────────────────

    #[test]
    fn web_fetch_resolved_private_ip_is_rejected() {
        let ips = vec!["127.0.0.1".parse().unwrap()];
        let err = validate_resolved_ips_are_public("example.com", &ips)
            .unwrap_err()
            .to_string();
        assert!(err.contains("non-global address"));
    }

    #[test]
    fn web_fetch_resolved_public_ips_are_allowed() {
        let ips = vec!["93.184.216.34".parse().unwrap(), "1.1.1.1".parse().unwrap()];
        assert!(validate_resolved_ips_are_public("example.com", &ips).is_ok());
    }

    // ── Chunk capping ───────────────────────────────────────────────────

    #[test]
    fn web_fetch_append_chunk_with_cap_truncates() {
        let mut buffer = Vec::new();
        assert!(!append_chunk_with_cap(&mut buffer, b"hello", 8));
        assert!(append_chunk_with_cap(&mut buffer, b"world", 8));
        assert_eq!(buffer, b"hellowor");
    }

    // ── build_tools_registry includes web_fetch ─────────────────────────

    #[test]
    fn web_fetch_registered_when_enabled() {
        let mut config = zeroclaw::Config::default();
        config.web_fetch.enabled = true;
        config.web_fetch.allowed_domains = vec!["*".into()];

        let memory = Arc::new(zeroclaw::memory::MarkdownMemory::new(
            &std::env::temp_dir().join("test_web_fetch_registry"),
        ));
        let tools = build_tools_registry(&config, memory);
        assert!(tools.iter().any(|t| t.name() == "web_fetch"));
    }

    #[test]
    fn web_fetch_not_registered_when_disabled() {
        let config = zeroclaw::Config::default();
        let memory = Arc::new(zeroclaw::memory::MarkdownMemory::new(
            &std::env::temp_dir().join("test_web_fetch_disabled"),
        ));
        let tools = build_tools_registry(&config, memory);
        assert!(!tools.iter().any(|t| t.name() == "web_fetch"));
    }

    // ── build_android_tool_descs includes web_fetch/web_search ──────────

    #[test]
    fn tool_descs_include_web_fetch_when_enabled() {
        let mut config = zeroclaw::Config::default();
        config.web_fetch.enabled = true;
        let descs = build_android_tool_descs(&config);
        assert!(descs.iter().any(|(name, _)| name == "web_fetch"));
    }

    #[test]
    fn tool_descs_include_web_search_when_enabled() {
        let mut config = zeroclaw::Config::default();
        config.web_search.enabled = true;
        let descs = build_android_tool_descs(&config);
        assert!(descs.iter().any(|(name, _)| name == "web_search"));
    }

    #[test]
    fn tool_descs_exclude_web_fetch_when_disabled() {
        let config = zeroclaw::Config::default();
        let descs = build_android_tool_descs(&config);
        assert!(!descs.iter().any(|(name, _)| name == "web_fetch"));
    }

    #[test]
    fn tool_descs_exclude_web_search_when_disabled() {
        let config = zeroclaw::Config::default();
        let descs = build_android_tool_descs(&config);
        assert!(!descs.iter().any(|(name, _)| name == "web_search"));
    }
}
