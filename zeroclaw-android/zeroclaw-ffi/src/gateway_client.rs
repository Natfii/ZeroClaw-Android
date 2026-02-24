/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Internal HTTP client for calling the daemon's gateway REST API.
//!
//! Upstream v0.1.6 made several modules (`cron`, `cost`, `health`,
//! `skills`) `pub(crate)`, so the FFI crate can no longer call those
//! functions directly. Instead, we route CRUD operations through the
//! gateway's REST API on the localhost loopback, reusing the same
//! pattern that [`crate::runtime::send_message_inner`] already uses
//! for `/webhook`.
//!
//! All calls go to `http://127.0.0.1:{gateway_port}{path}`.

use crate::error::FfiError;
use tokio::time::Duration;

/// Timeout for gateway API calls (30 seconds).
const API_TIMEOUT_SECS: u64 = 30;

/// Performs an HTTP GET against the gateway and returns the parsed JSON body.
pub(crate) fn gateway_get(path: &str) -> Result<serde_json::Value, FfiError> {
    let port = crate::runtime::get_gateway_port()?;
    let runtime = crate::runtime::get_or_create_runtime()?;
    let url = format!("http://127.0.0.1:{port}{path}");

    runtime.block_on(async {
        let client = build_client()?;
        let response = client
            .get(&url)
            .send()
            .await
            .map_err(|e| FfiError::SpawnError {
                detail: format!("gateway GET {path} failed: {e}"),
            })?;
        parse_response(response, path).await
    })
}

/// Performs an HTTP POST against the gateway and returns the parsed JSON body.
pub(crate) fn gateway_post(
    path: &str,
    body: &serde_json::Value,
) -> Result<serde_json::Value, FfiError> {
    let port = crate::runtime::get_gateway_port()?;
    let runtime = crate::runtime::get_or_create_runtime()?;
    let url = format!("http://127.0.0.1:{port}{path}");

    runtime.block_on(async {
        let client = build_client()?;
        let response =
            client
                .post(&url)
                .json(body)
                .send()
                .await
                .map_err(|e| FfiError::SpawnError {
                    detail: format!("gateway POST {path} failed: {e}"),
                })?;
        parse_response(response, path).await
    })
}

/// Performs an HTTP DELETE against the gateway and returns the parsed JSON body.
pub(crate) fn gateway_delete(path: &str) -> Result<serde_json::Value, FfiError> {
    let port = crate::runtime::get_gateway_port()?;
    let runtime = crate::runtime::get_or_create_runtime()?;
    let url = format!("http://127.0.0.1:{port}{path}");

    runtime.block_on(async {
        let client = build_client()?;
        let response = client
            .delete(&url)
            .send()
            .await
            .map_err(|e| FfiError::SpawnError {
                detail: format!("gateway DELETE {path} failed: {e}"),
            })?;
        parse_response(response, path).await
    })
}

/// Builds a reqwest client with our standard timeout.
fn build_client() -> Result<reqwest::Client, FfiError> {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(API_TIMEOUT_SECS))
        .build()
        .map_err(|e| FfiError::SpawnError {
            detail: format!("failed to build HTTP client: {e}"),
        })
}

/// Parses a gateway response, returning the JSON body or an error.
async fn parse_response(
    response: reqwest::Response,
    path: &str,
) -> Result<serde_json::Value, FfiError> {
    let status = response.status();
    if !status.is_success() {
        let body_text = response.text().await.unwrap_or_default();
        return Err(FfiError::SpawnError {
            detail: format!("gateway {path} returned {status}: {body_text}"),
        });
    }
    response
        .json::<serde_json::Value>()
        .await
        .map_err(|e| FfiError::SpawnError {
            detail: format!("failed to parse gateway {path} response: {e}"),
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gateway_get_not_running() {
        let result = gateway_get("/api/health");
        match result {
            Err(FfiError::StateError { detail }) => {
                assert!(detail.contains("not running"));
            }
            Err(other) => panic!("expected StateError, got {other:?}"),
            Ok(_) => panic!("expected error, got Ok"),
        }
    }

    #[test]
    fn test_gateway_post_not_running() {
        let result = gateway_post("/api/cron", &serde_json::json!({}));
        assert!(result.is_err());
    }

    #[test]
    fn test_gateway_delete_not_running() {
        let result = gateway_delete("/api/cron/test-id");
        assert!(result.is_err());
    }
}
