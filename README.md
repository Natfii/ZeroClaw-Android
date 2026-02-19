# ZeroClaw-Android: Run AI Agents 24/7 on Your Phone

<img width="1024" height="335" alt="image hero banner" src="https://github.com/user-attachments/assets/03b919d9-428b-4372-a2f7-d2b9876b2fec" />

> Your old phone in a drawer is a better AI server than you think.

[![CI](https://github.com/Natfii/ZeroClaw-Android/actions/workflows/ci.yml/badge.svg)](https://github.com/Natfii/ZeroClaw-Android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![API 26+](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Latest Release](https://img.shields.io/github/v/release/Natfii/ZeroClaw-Android?include_prereleases&label=release)](https://github.com/Natfii/ZeroClaw-Android/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Rust](https://img.shields.io/badge/Rust-stable-DEA584.svg?logo=rust&logoColor=white)](https://www.rust-lang.org)
[![Providers](https://img.shields.io/badge/Providers-29%2B-blue.svg)](https://github.com/Natfii/ZeroClaw-Android#supported-providers)

ZeroClaw-Android turns your Android phone into an always-on AI agent host. Not a Termux hack. Not a WebView. Native Rust compiled to ARM. Connect to 29+ providers -- OpenAI, Claude, Gemini, Groq, DeepSeek, Ollama, LM Studio, vLLM, and more -- and run autonomous agents around the clock with encrypted API key storage (AES-256-GCM) and a battery-optimized foreground service. No server required. No cloud bills. Just your phone.

## Quick Start

Got an old phone? Give it a new job.

1. **Download** the latest APK from [GitHub Releases](https://github.com/Natfii/ZeroClaw-Android/releases)
2. **Add a provider** -- paste your API key or point to a local Ollama instance
3. **Create an agent** -- pick a model and configure its behavior
4. **Tap Start** -- the daemon launches as a foreground service and runs until you stop it

The onboarding wizard walks you through all of this on first launch.

***Disclaimer: This app is a personal project not associated with the ZeroClaw Labs team. It might break.***

## Features

- **29 providers** -- OpenAI, Anthropic, Gemini, Groq, DeepSeek, Mistral, Ollama, LM Studio, vLLM, LocalAI, OpenRouter, Together AI, Cohere, Perplexity, xAI, and more. Plus custom OpenAI-compatible endpoints.
- **Plugin browser** -- extend agents with tools for web search, code execution, file access, MQTT, and webhooks
- **Encrypted key storage** -- AES-256-GCM via Android Keystore, hardware-backed on StrongBox devices, biometric unlock to reveal
- **Battery-optimized** -- `START_STICKY` foreground service with OEM battery killer detection, auto-restart on boot, network transition handling
- **Material You** -- dynamic color theming on Android 12+, adaptive navigation (bottom bar / rail / drawer), WCAG 2.2 AA accessibility
- **Auto-restart** -- your agents survive reboot, survive task kill, survive sleep. Exponential backoff on failures.
- **Rust core** -- ZeroClaw's router runs natively via UniFFI, with `catch_unwind` at every FFI boundary. No JNI crashes.

<img src="https://github.com/user-attachments/assets/9712875d-5650-43a4-9646-8aa4d4175291" alt="On Boarding" width="30%" /> <img src="https://github.com/user-attachments/assets/ae198cdc-2130-4b25-8e69-114468653777" alt="Nav" width="37.5%" /> <img src="https://github.com/user-attachments/assets/4f1dc8bb-8cf7-4ce3-8321-860060ab98f6" alt="Nav" width="30%" /> 

## Why a Phone?

You already carry a computer with a multi-core ARM chip, 8GB RAM, always-on connectivity, and a built-in battery backup. Why buy another one?

Phones are designed to stay on. They handle push notifications, background services, and power management better than any Raspberry Pi. Add a cellular fallback and WiFi, and you have an always-connected edge device in your pocket.

**Best for:**

- Routing calls to cloud providers (OpenAI, Claude, Gemini)
- Running lightweight local models via Ollama on your network
- IoT agent hubs that need always-on connectivity
- Personal automation that runs while your laptop sleeps

**Not ideal for:**

- Running large local models on-device (use a desktop GPU or Ollama server)
- Workloads that need >8GB RAM
- Latency-critical applications under 100ms

### Phone vs Mac Mini

|                   | Old Android Phone               | Mac Mini M4                     |
| ----------------- | ------------------------------- | ------------------------------- |
| Cost              | $0 (you already own it)         | $499+                           |
| Power draw        | 2-5W idle                       | 10-25W idle                     |
| Battery backup    | Built in                        | Requires UPS ($50+)             |
| Cellular fallback | Built in                        | Requires hotspot or dongle      |
| Always-on design  | Yes (it's a phone)              | Yes (but no battery)            |
| Local inference   | Limited (API routing)           | Strong (16-32GB unified memory) |
| Setup time        | 5 minutes                       | 30+ minutes                     |
| Also a phone      | Yes                             | No                              |

## Security

API keys are stored in `EncryptedSharedPreferences` backed by Android Keystore with AES-256-GCM encryption. On devices with a hardware security module (StrongBox), the master key is hardware-backed and never leaves the secure enclave.

- Keys are masked by default (last 4 characters visible)
- Biometric authentication required to reveal full keys
- Encrypted export/import with Argon2 key derivation
- Rust core uses memory-safe FFI -- no buffer overflows, no use-after-free
- The app is not distributed through third-party marketplaces. Builds are reproducible from source.

Your API keys never leave your device unencrypted. Not to a cloud. Not to a marketplace. Not to us.

## Supported Providers

OpenAI, Anthropic (Claude), Google Gemini, Ollama, LM Studio, vLLM, LocalAI, OpenRouter, Groq, DeepSeek, Mistral, xAI (Grok), Together AI, Fireworks AI, Perplexity, Cohere, GitHub Copilot, Amazon Bedrock, Cloudflare AI, and more. Any OpenAI-compatible endpoint works via the custom provider option.

<details>
<summary>Full Provider Matrix</summary>

| Provider                    | Auth Type          | Category  |
| --------------------------- | ------------------ | --------- |
| OpenAI                      | API Key            | Primary   |
| Anthropic                   | API Key            | Primary   |
| Google Gemini               | API Key            | Primary   |
| OpenRouter                  | API Key            | Primary   |
| Ollama                      | URL only           | Primary   |
| LM Studio                   | URL + optional key | Primary   |
| vLLM                        | URL + optional key | Primary   |
| LocalAI                     | URL + optional key | Primary   |
| Groq                        | API Key            | Ecosystem |
| Mistral                     | API Key            | Ecosystem |
| xAI / Grok                  | API Key            | Ecosystem |
| DeepSeek                    | API Key            | Ecosystem |
| Together AI                 | API Key            | Ecosystem |
| Fireworks AI                | API Key            | Ecosystem |
| Perplexity                  | API Key            | Ecosystem |
| Cohere                      | API Key            | Ecosystem |
| GitHub Copilot              | API Key            | Ecosystem |
| Venice                      | API Key            | Ecosystem |
| Vercel AI                   | API Key            | Ecosystem |
| Moonshot / Kimi             | API Key            | Ecosystem |
| MiniMax                     | API Key            | Ecosystem |
| GLM / Zhipu                 | API Key            | Ecosystem |
| Qianfan / Baidu             | API Key            | Ecosystem |
| Cloudflare AI               | URL + optional key | Ecosystem |
| Amazon Bedrock              | URL + optional key | Ecosystem |
| Synthetic                   | None               | Ecosystem |
| OpenCode Zen                | API Key            | Ecosystem |
| Z.AI                        | API Key            | Ecosystem |
| Custom OpenAI-compatible    | URL + optional key | Custom    |
| Custom Anthropic-compatible | URL + optional key | Custom    |

</details>

## Architecture

Kotlin/Compose UI on top, Rust engine underneath, connected through Mozilla UniFFI. The Android foreground service manages the daemon lifecycle while Compose renders the management interface.

<details>
<summary>Architecture Details</summary>

**FFI surface** -- 5 functions cross the Rust-Kotlin boundary:

| Function                                      | Description                                     |
| --------------------------------------------- | ----------------------------------------------- |
| `start_daemon(config, dataDir, host, port)` | Start the ZeroClaw daemon with TOML config      |
| `stop_daemon()`                             | Signal shutdown and wait for all components     |
| `get_status()`                              | Returns JSON health snapshot                    |
| `send_message(msg)`                         | Send a message to the gateway, returns response |
| `get_version()`                             | Returns native library version string           |

**Key implementation details:**

- `catch_unwind` wraps every FFI export to prevent Rust panics from crashing the JVM
- The daemon runs on a dedicated Tokio multi-thread runtime
- Shutdown uses a `watch` channel (upstream `daemon::run()` blocks on `ctrl_c()`, which is unsuitable for FFI)
- Room database for persistent storage (agents, plugins, logs, activity events)
- DataStore for user preferences, EncryptedSharedPreferences for secrets

</details>

<details>
<summary>Ecosystem / Related Projects</summary>

| Project                                            | Description                        | Relationship                                                   |
| -------------------------------------------------- | ---------------------------------- | -------------------------------------------------------------- |
| [ZeroClaw](https://github.com/zeroclaw-labs/zeroclaw) | Rust-native AI agent framework     | Upstream core (git submodule)                                  |
| OpenClaw                                           | TypeScript-based AI agent platform | ZeroClaw is a Rust-native rewrite of the OpenClaw architecture |
| ZeroClaw-Android                                   | This project                       | Android wrapper with native FFI                                |

ZeroClaw-Android wraps the upstream ZeroClaw engine without modification. Nader Dabit has called ZeroClaw "insanely fast" -- this project brings that speed to Android as an always-on service.

</details>

<details>
<summary>Building from Source</summary>

### Prerequisites

| Tool        | Version        | Notes                                              |
| ----------- | -------------- | -------------------------------------------------- |
| JDK         | 17             | [Eclipse Adoptium](https://adoptium.net/) recommended |
| Android SDK | API 35         | Via Android Studio or `sdkmanager`               |
| Android NDK | r27c           | `sdkmanager "ndk;27.2.12479018"`                 |
| Rust        | stable (1.85+) | [rustup.rs](https://rustup.rs/)                       |
| cargo-ndk   | 4.x            | `cargo install cargo-ndk`                        |

### Setup

```bash
# Install Rust Android targets
rustup target add aarch64-linux-android x86_64-linux-android

# Clone with submodules
git clone --recursive https://github.com/Natfii/ZeroClaw-Android.git
cd ZeroClaw-Android

# Set environment (adjust paths for your system)
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/Android/Sdk"
```

### Build

```bash
# Debug build (compiles Rust via cargo-ndk automatically)
./gradlew :app:assembleDebug

# Run tests
./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest

# Lint checks
./gradlew spotlessCheck detekt
```

The Gradle build invokes `cargo-ndk` via [Gobley](https://github.com/aspect-build/gobley) to cross-compile the Rust FFI library and generate UniFFI Kotlin bindings. No manual Rust build step needed.

### Project structure

```
ZeroClaw-Android/
  app/                    Android app (Kotlin/Compose)
  lib/                    Library module (AAR publishing)
  zeroclaw/               Upstream ZeroClaw (git submodule)
  zeroclaw-android/       Cargo workspace
    zeroclaw-ffi/         UniFFI-annotated Rust facade
  .github/workflows/      CI, upstream sync, release
```

</details>

<details>
<summary>FAQ</summary>

**Does this run AI models on the phone itself?**
Not directly. ZeroClaw-Android runs the agent *router* -- it manages which models to call, handles tool execution, and orchestrates multi-step workflows. Inference happens on the provider's servers (OpenAI, Claude, etc.) or on a local Ollama instance on your network.

**How much battery does it use?**
The foreground service is idle most of the time, waking only when an agent needs to act. Typical usage is 2-5% battery per day. The app detects battery saver mode and reduces animations and non-essential work.

**Will my phone manufacturer kill the service?**
Some OEMs (Xiaomi, Samsung, Huawei, OnePlus) aggressively kill background services. The app detects these manufacturers and shows a one-time banner linking to [dontkillmyapp.com](https://dontkillmyapp.com) with device-specific instructions.

**What Android versions are supported?**
Android 8.0 (API 26) and above. Material You dynamic theming requires Android 12+, but the app works with a static color scheme on older versions.

**Can I use this without an API key?**
Yes, if you connect to a local Ollama instance or use the Synthetic provider for testing. Most cloud providers require an API key.

**Is there a Google Play release?**
Not yet. Distribution is via GitHub Releases for now. Google Play is on the roadmap, along with expanded plugin support and Tasker/automation integration.

**Can I run multiple agents simultaneously?**
Yes. ZeroClaw's router supports multiple agents with independent configurations, each connected to different providers if needed.

</details>

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

<!-- GitHub repo settings (not rendered):
  About/Description: Run AI agents 24/7 on your Android phone. Native Rust core, 29+ providers (OpenAI, Claude, Gemini, Groq, DeepSeek, Ollama, LM Studio, vLLM), encrypted key storage, plugin browser, Material You UI. Self-hosted alternative to Mac Mini setups. MIT licensed.
  Topics: ai-agent, android, rust, openai, anthropic, self-hosted, llm, foreground-service, kotlin, ai-agent-framework, material-you, ollama, groq, deepseek, gemini, iot, mqtt, encrypted-storage, jetpack-compose, zeroclaw
-->
