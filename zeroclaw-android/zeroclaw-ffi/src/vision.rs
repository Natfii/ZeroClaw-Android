/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Direct-to-provider multimodal (vision) API dispatch.
//!
//! Bypasses `ZeroClaw`'s text-only agent loop and calls provider APIs
//! directly for image+text requests. Supports Anthropic Messages API,
//! OpenAI Chat Completions (and compatible endpoints), and Google
//! Gemini `GenerateContent`.

use crate::error::FfiError;
use crate::runtime::{get_or_create_runtime, with_daemon_config};
use serde_json::{Value, json};
use tokio::time::Duration;

/// Maximum number of images per vision request.
const MAX_IMAGES: usize = 5;

/// HTTP timeout for vision API calls (5 minutes).
const VISION_TIMEOUT_SECS: u64 = 300;

/// Default `max_tokens` for vision API responses (Anthropic and OpenAI-compatible).
const DEFAULT_MAX_TOKENS: u64 = 4096;

/// Supported vision API wire formats.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum VisionProvider {
    /// Anthropic Messages API (base64 image source).
    Anthropic {
        /// Base URL override; `None` means `https://api.anthropic.com`.
        base_url: Option<String>,
    },
    /// OpenAI Chat Completions or any compatible endpoint.
    OpenAi {
        /// Base URL override; `None` means `https://api.openai.com`.
        base_url: Option<String>,
    },
    /// Google Gemini `GenerateContent` endpoint.
    Gemini,
}

/// Maps a `ZeroClaw` provider name to the vision wire format.
///
/// Returns `None` for providers that do not support vision or whose
/// API format is unknown.
pub(crate) fn classify_provider(name: &str) -> Option<VisionProvider> {
    match name.to_lowercase().as_str() {
        // Anthropic family
        "anthropic" | "claude" => Some(VisionProvider::Anthropic { base_url: None }),
        // Custom Anthropic endpoint — extract URL after the prefix
        s if s.starts_with("anthropic-custom:") => {
            let url = s
                .strip_prefix("anthropic-custom:")
                .unwrap_or("")
                .to_string();
            Some(VisionProvider::Anthropic {
                base_url: if url.is_empty() { None } else { Some(url) },
            })
        }

        // Native OpenAI
        "openai" | "gpt" | "chatgpt" => Some(VisionProvider::OpenAi { base_url: None }),

        // OpenAI-compatible providers (use OpenAI wire format)
        "openrouter" => Some(VisionProvider::OpenAi {
            base_url: Some("https://openrouter.ai/api/v1".into()),
        }),
        "together" | "together-ai" | "togetherai" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.together.xyz/v1".into()),
        }),
        "groq" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.groq.com/openai/v1".into()),
        }),
        "perplexity" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.perplexity.ai".into()),
        }),
        "deepseek" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.deepseek.com/v1".into()),
        }),
        "fireworks" | "fireworks-ai" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.fireworks.ai/inference/v1".into()),
        }),
        "mistral" => Some(VisionProvider::OpenAi {
            base_url: Some("https://api.mistral.ai/v1".into()),
        }),

        // Custom OpenAI-compatible — extract URL after the prefix
        s if s.starts_with("custom:") => {
            let url = s.strip_prefix("custom:").unwrap_or("").to_string();
            Some(VisionProvider::OpenAi {
                base_url: Some(url),
            })
        }

        // Google Gemini family
        "gemini" | "google" | "google-ai" => Some(VisionProvider::Gemini),

        // Local inference (OpenAI-compatible, each with its default port)
        "ollama" => Some(VisionProvider::OpenAi {
            base_url: Some("http://localhost:11434/v1".into()),
        }),
        "lmstudio" => Some(VisionProvider::OpenAi {
            base_url: Some("http://localhost:1234/v1".into()),
        }),
        "vllm" => Some(VisionProvider::OpenAi {
            base_url: Some("http://localhost:8000/v1".into()),
        }),
        "localai" => Some(VisionProvider::OpenAi {
            base_url: Some("http://localhost:8080/v1".into()),
        }),

        _ => None,
    }
}

/// Builds an Anthropic Messages API request body.
pub(crate) fn build_anthropic_body(
    text: &str,
    image_data: &[String],
    mime_types: &[String],
    model: &str,
) -> Value {
    let mut content = Vec::with_capacity(image_data.len() + 1);
    for (data, mime) in image_data.iter().zip(mime_types.iter()) {
        content.push(json!({
            "type": "image",
            "source": {
                "type": "base64",
                "media_type": mime,
                "data": data,
            }
        }));
    }
    content.push(json!({
        "type": "text",
        "text": text,
    }));

    json!({
        "model": model,
        "max_tokens": DEFAULT_MAX_TOKENS,
        "messages": [{
            "role": "user",
            "content": content,
        }]
    })
}

/// Builds an OpenAI Chat Completions request body (also used by
/// compatible providers like OpenRouter, Together, Groq, etc.).
pub(crate) fn build_openai_body(
    text: &str,
    image_data: &[String],
    mime_types: &[String],
    model: &str,
) -> Value {
    let mut content = Vec::with_capacity(image_data.len() + 1);
    for (data, mime) in image_data.iter().zip(mime_types.iter()) {
        content.push(json!({
            "type": "image_url",
            "image_url": {
                "url": format!("data:{mime};base64,{data}"),
                "detail": "auto",
            }
        }));
    }
    content.push(json!({
        "type": "text",
        "text": text,
    }));

    json!({
        "model": model,
        "max_tokens": DEFAULT_MAX_TOKENS,
        "messages": [{
            "role": "user",
            "content": content,
        }]
    })
}

/// Builds a Google Gemini `GenerateContent` request body.
pub(crate) fn build_gemini_body(text: &str, image_data: &[String], mime_types: &[String]) -> Value {
    let mut parts = Vec::with_capacity(image_data.len() + 1);
    for (data, mime) in image_data.iter().zip(mime_types.iter()) {
        parts.push(json!({
            "inline_data": {
                "mime_type": mime,
                "data": data,
            }
        }));
    }
    parts.push(json!({ "text": text }));

    json!({
        "contents": [{
            "parts": parts,
        }]
    })
}

/// Extracts the assistant text from an Anthropic Messages API response.
pub(crate) fn parse_anthropic_response(body: &Value) -> Result<String, FfiError> {
    body["content"]
        .as_array()
        .and_then(|blocks| {
            blocks.iter().find_map(|b| {
                if b["type"].as_str() == Some("text") {
                    b["text"].as_str().map(String::from)
                } else {
                    None
                }
            })
        })
        .ok_or_else(|| FfiError::SpawnError {
            detail: "Anthropic response missing text content block".into(),
        })
}

/// Extracts the assistant text from an OpenAI Chat Completions response.
pub(crate) fn parse_openai_response(body: &Value) -> Result<String, FfiError> {
    body["choices"]
        .as_array()
        .and_then(|choices| choices.first())
        .and_then(|choice| choice["message"]["content"].as_str())
        .map(String::from)
        .ok_or_else(|| FfiError::SpawnError {
            detail: "OpenAI response missing choices[0].message.content".into(),
        })
}

/// Extracts the assistant text from a Google Gemini response.
pub(crate) fn parse_gemini_response(body: &Value) -> Result<String, FfiError> {
    body["candidates"]
        .as_array()
        .and_then(|candidates| candidates.first())
        .and_then(|candidate| candidate["content"]["parts"].as_array())
        .and_then(|parts| parts.iter().find_map(|p| p["text"].as_str()))
        .map(String::from)
        .ok_or_else(|| FfiError::SpawnError {
            detail: "Gemini response missing candidates[0].content.parts[].text".into(),
        })
}

/// Sends a vision (image + text) message directly to the configured provider.
///
/// Reads the active provider, model, and API key from `DaemonState`
/// config, builds the appropriate request body, and dispatches an
/// HTTP POST. Returns the assistant's text reply.
///
/// # Errors
///
/// Returns [`FfiError::ConfigError`] for validation failures (empty
/// images, too many images, mismatched counts),
/// [`FfiError::StateError`] if the daemon is not running,
/// [`FfiError::SpawnError`] for unsupported providers, HTTP failures,
/// or response parse errors.
pub(crate) fn send_vision_message_inner(
    text: String,
    image_data: Vec<String>,
    mime_types: Vec<String>,
) -> Result<String, FfiError> {
    validate_vision_input(&image_data, &mime_types)?;

    let (provider_name, api_key, model) = with_daemon_config(|config| {
        let provider = config
            .default_provider
            .clone()
            .unwrap_or_else(|| "anthropic".to_string());
        let key = config.api_key.clone().unwrap_or_default();
        let mdl = config
            .default_model
            .clone()
            .unwrap_or_else(|| "claude-sonnet-4-20250514".to_string());
        (provider, key, mdl)
    })?;

    let vision_provider =
        classify_provider(&provider_name).ok_or_else(|| FfiError::SpawnError {
            detail: format!(
                "Vision not supported for provider: {provider_name}. \
                 Use Anthropic, OpenAI, Gemini, or an OpenAI-compatible provider."
            ),
        })?;

    let runtime = get_or_create_runtime();
    runtime.block_on(dispatch_vision_request(
        &vision_provider,
        &text,
        &image_data,
        &mime_types,
        &model,
        &api_key,
    ))
}

/// Validates vision request inputs before dispatching.
fn validate_vision_input(image_data: &[String], mime_types: &[String]) -> Result<(), FfiError> {
    if image_data.is_empty() {
        return Err(FfiError::ConfigError {
            detail: "at least one image is required".into(),
        });
    }
    if image_data.len() > MAX_IMAGES {
        return Err(FfiError::ConfigError {
            detail: format!("too many images ({}, max {MAX_IMAGES})", image_data.len()),
        });
    }
    if image_data.len() != mime_types.len() {
        return Err(FfiError::ConfigError {
            detail: format!(
                "image_data length ({}) != mime_types length ({})",
                image_data.len(),
                mime_types.len()
            ),
        });
    }
    Ok(())
}

/// Builds the HTTP request, sends it, and parses the provider response.
async fn dispatch_vision_request(
    provider: &VisionProvider,
    text: &str,
    image_data: &[String],
    mime_types: &[String],
    model: &str,
    api_key: &str,
) -> Result<String, FfiError> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(VISION_TIMEOUT_SECS))
        .build()
        .map_err(|e| FfiError::SpawnError {
            detail: format!("failed to build HTTP client: {e}"),
        })?;

    let (url, body) = match provider {
        VisionProvider::Anthropic { base_url } => {
            let base = base_url.as_deref().unwrap_or("https://api.anthropic.com");
            let body = build_anthropic_body(text, image_data, mime_types, model);
            (format!("{base}/v1/messages"), body)
        }
        VisionProvider::OpenAi { base_url } => {
            let base = base_url.as_deref().unwrap_or("https://api.openai.com/v1");
            let body = build_openai_body(text, image_data, mime_types, model);
            (format!("{base}/chat/completions"), body)
        }
        VisionProvider::Gemini => {
            let body = build_gemini_body(text, image_data, mime_types);
            let url = format!(
                "https://generativelanguage.googleapis.com/v1beta/models/\
                 {model}:generateContent?key={api_key}"
            );
            (url, body)
        }
    };

    let mut request = client.post(&url).json(&body);
    match provider {
        VisionProvider::Anthropic { .. } => {
            request = request
                .header("x-api-key", api_key)
                .header("anthropic-version", "2023-06-01");
        }
        VisionProvider::OpenAi { .. } => {
            request = request.header("Authorization", format!("Bearer {api_key}"));
        }
        VisionProvider::Gemini => {}
    }

    let response = request.send().await.map_err(|e| FfiError::SpawnError {
        detail: format!("vision API request failed: {e}"),
    })?;

    let status = response.status();
    if !status.is_success() {
        let error_body = response.text().await.unwrap_or_default();
        let truncated = if error_body.len() > 500 {
            format!("{}...", &error_body[..500])
        } else {
            error_body
        };
        return Err(FfiError::SpawnError {
            detail: format!("vision API returned status {status}: {truncated}"),
        });
    }

    let response_body: Value = response.json().await.map_err(|e| FfiError::SpawnError {
        detail: format!("failed to parse vision API response: {e}"),
    })?;

    match provider {
        VisionProvider::Anthropic { .. } => parse_anthropic_response(&response_body),
        VisionProvider::OpenAi { .. } => parse_openai_response(&response_body),
        VisionProvider::Gemini => parse_gemini_response(&response_body),
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;

    // ── classify_provider tests ────────────────────────────────────

    #[test]
    fn test_classify_anthropic() {
        assert_eq!(
            classify_provider("anthropic"),
            Some(VisionProvider::Anthropic { base_url: None })
        );
        assert_eq!(
            classify_provider("claude"),
            Some(VisionProvider::Anthropic { base_url: None })
        );
        assert_eq!(
            classify_provider("Anthropic"),
            Some(VisionProvider::Anthropic { base_url: None })
        );
    }

    #[test]
    fn test_classify_anthropic_custom() {
        let result = classify_provider("anthropic-custom:https://my-proxy.example.com");
        assert_eq!(
            result,
            Some(VisionProvider::Anthropic {
                base_url: Some("https://my-proxy.example.com".into()),
            })
        );
    }

    #[test]
    fn test_classify_openai() {
        assert_eq!(
            classify_provider("openai"),
            Some(VisionProvider::OpenAi { base_url: None })
        );
        assert_eq!(
            classify_provider("gpt"),
            Some(VisionProvider::OpenAi { base_url: None })
        );
        assert_eq!(
            classify_provider("chatgpt"),
            Some(VisionProvider::OpenAi { base_url: None })
        );
    }

    #[test]
    fn test_classify_openai_compatible() {
        let check = |name: &str| {
            let result = classify_provider(name);
            assert!(
                matches!(result, Some(VisionProvider::OpenAi { base_url: Some(_) })),
                "expected OpenAi with base_url for {name}, got {result:?}"
            );
        };
        check("openrouter");
        check("together");
        check("groq");
        check("perplexity");
        check("deepseek");
        check("fireworks");
        check("mistral");
    }

    #[test]
    fn test_classify_custom_url() {
        let result = classify_provider("custom:http://localhost:8080/v1");
        assert_eq!(
            result,
            Some(VisionProvider::OpenAi {
                base_url: Some("http://localhost:8080/v1".into()),
            })
        );
    }

    #[test]
    fn test_classify_gemini() {
        assert_eq!(classify_provider("gemini"), Some(VisionProvider::Gemini));
        assert_eq!(classify_provider("google"), Some(VisionProvider::Gemini));
    }

    #[test]
    fn test_classify_local() {
        for name in &["ollama", "lmstudio", "vllm", "localai"] {
            let result = classify_provider(name);
            assert!(
                matches!(result, Some(VisionProvider::OpenAi { base_url: Some(_) })),
                "expected OpenAi with base_url for {name}, got {result:?}"
            );
        }
    }

    #[test]
    fn test_classify_local_ports() {
        let check_port = |name: &str, expected_port: &str| {
            let result = classify_provider(name);
            match result {
                Some(VisionProvider::OpenAi {
                    base_url: Some(url),
                }) => {
                    assert!(
                        url.contains(expected_port),
                        "expected port {expected_port} for {name}, got URL: {url}"
                    );
                }
                other => panic!("expected OpenAi for {name}, got {other:?}"),
            }
        };
        check_port("ollama", ":11434");
        check_port("lmstudio", ":1234");
        check_port("vllm", ":8000");
        check_port("localai", ":8080");
    }

    #[test]
    fn test_classify_anthropic_custom_empty() {
        let result = classify_provider("anthropic-custom:");
        assert_eq!(result, Some(VisionProvider::Anthropic { base_url: None }));
    }

    #[test]
    fn test_classify_unsupported() {
        assert_eq!(classify_provider("unknown-provider-xyz"), None);
        assert_eq!(classify_provider(""), None);
    }

    // ── build body tests ───────────────────────────────────────────

    #[test]
    fn test_build_anthropic_body_single_image() {
        let body = build_anthropic_body(
            "describe this",
            &["aGVsbG8=".into()],
            &["image/jpeg".into()],
            "claude-sonnet-4-20250514",
        );
        assert_eq!(body["model"], "claude-sonnet-4-20250514");
        assert_eq!(body["max_tokens"], DEFAULT_MAX_TOKENS);
        let content = body["messages"][0]["content"].as_array().unwrap();
        assert_eq!(content.len(), 2);
        assert_eq!(content[0]["type"], "image");
        assert_eq!(content[0]["source"]["type"], "base64");
        assert_eq!(content[0]["source"]["media_type"], "image/jpeg");
        assert_eq!(content[0]["source"]["data"], "aGVsbG8=");
        assert_eq!(content[1]["type"], "text");
        assert_eq!(content[1]["text"], "describe this");
    }

    #[test]
    fn test_build_anthropic_body_multiple_images() {
        let body = build_anthropic_body(
            "compare",
            &["img1".into(), "img2".into(), "img3".into()],
            &["image/jpeg".into(), "image/png".into(), "image/jpeg".into()],
            "claude-sonnet-4-20250514",
        );
        let content = body["messages"][0]["content"].as_array().unwrap();
        assert_eq!(content.len(), 4);
        assert_eq!(content[2]["source"]["media_type"], "image/jpeg");
    }

    #[test]
    fn test_build_openai_body_single_image() {
        let body = build_openai_body(
            "what is this?",
            &["aGVsbG8=".into()],
            &["image/jpeg".into()],
            "gpt-4o",
        );
        assert_eq!(body["model"], "gpt-4o");
        assert_eq!(body["max_tokens"], DEFAULT_MAX_TOKENS);
        let content = body["messages"][0]["content"].as_array().unwrap();
        assert_eq!(content.len(), 2);
        assert_eq!(content[0]["type"], "image_url");
        let url = content[0]["image_url"]["url"].as_str().unwrap();
        assert!(url.starts_with("data:image/jpeg;base64,"));
        assert_eq!(content[0]["image_url"]["detail"], "auto");
        assert_eq!(content[1]["text"], "what is this?");
    }

    #[test]
    fn test_build_gemini_body_single_image() {
        let body = build_gemini_body("analyze", &["aGVsbG8=".into()], &["image/jpeg".into()]);
        let parts = body["contents"][0]["parts"].as_array().unwrap();
        assert_eq!(parts.len(), 2);
        assert_eq!(parts[0]["inline_data"]["mime_type"], "image/jpeg");
        assert_eq!(parts[0]["inline_data"]["data"], "aGVsbG8=");
        assert_eq!(parts[1]["text"], "analyze");
    }

    // ── parse response tests ───────────────────────────────────────

    #[test]
    fn test_parse_anthropic_response_ok() {
        let body = json!({
            "content": [
                {"type": "text", "text": "This is a cat."}
            ]
        });
        assert_eq!(parse_anthropic_response(&body).unwrap(), "This is a cat.");
    }

    #[test]
    fn test_parse_anthropic_response_missing() {
        let body = json!({"content": []});
        assert!(parse_anthropic_response(&body).is_err());
    }

    #[test]
    fn test_parse_openai_response_ok() {
        let body = json!({
            "choices": [{
                "message": {"content": "A beautiful sunset."}
            }]
        });
        assert_eq!(parse_openai_response(&body).unwrap(), "A beautiful sunset.");
    }

    #[test]
    fn test_parse_openai_response_missing() {
        let body = json!({"choices": []});
        assert!(parse_openai_response(&body).is_err());
    }

    #[test]
    fn test_parse_gemini_response_ok() {
        let body = json!({
            "candidates": [{
                "content": {
                    "parts": [{"text": "A dog playing."}]
                }
            }]
        });
        assert_eq!(parse_gemini_response(&body).unwrap(), "A dog playing.");
    }

    #[test]
    fn test_parse_gemini_response_missing() {
        let body = json!({"candidates": []});
        assert!(parse_gemini_response(&body).is_err());
    }

    // ── input validation tests ─────────────────────────────────────

    #[test]
    fn test_send_vision_empty_images() {
        let result = send_vision_message_inner("hello".into(), vec![], vec![]);
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("at least one image"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_send_vision_too_many_images() {
        let result = send_vision_message_inner(
            "hello".into(),
            vec!["a".into(); 6],
            vec!["image/jpeg".into(); 6],
        );
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("too many images"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }

    #[test]
    fn test_send_vision_mismatched_lengths() {
        let result = send_vision_message_inner(
            "hello".into(),
            vec!["a".into()],
            vec!["image/jpeg".into(), "image/png".into()],
        );
        assert!(result.is_err());
        match result.unwrap_err() {
            FfiError::ConfigError { detail } => {
                assert!(detail.contains("length"));
            }
            other => panic!("expected ConfigError, got {other:?}"),
        }
    }
}
