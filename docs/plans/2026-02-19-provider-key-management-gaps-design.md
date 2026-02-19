# Provider / Key Management Gaps — Design

**Date**: 2026-02-19
**Status**: Approved

## Problem

The ZeroClaw Android app has five interconnected gaps in the provider and API key management
flow that cause the console to return "gateway returned status 500 internal server error" under
specific but common usage patterns:

1. A process death (or OEM kill) restores the daemon from a stale persisted TOML that contains
   keys as they were at last-start time, not current settings.
2. Adding, rotating, or updating a key while the daemon is running has no visible effect — no
   restart banner is shown.
3. Deleting the key for the current `defaultProvider` leaves a dangling provider name. The next
   startup assembles TOML with `default_provider = "anthropic"` but no `api_key`, and every
   subsequent webhook call returns 500.
4. Expired OAuth tokens (e.g. Anthropic `sk-ant-oat01-*`) fail silently on refresh. The stale
   expired token is returned to the daemon anyway, Anthropic rejects it, and the gateway returns
   500 — indistinguishable from any other error.
5. (Root cause of Gap 4) The `EncryptedApiKeyRepository.getByProviderFresh()` method does not
   mark an OAuth key as invalid when refresh fails, so the UI shows no warning.

## Scope

Four files, no new public interfaces, no schema changes, no upstream changes.

## Design

### Gap 1 — `handleStickyRestart` rebuilds fresh (`ZeroClawDaemonService`, `DaemonPersistence`)

**Before**: `handleStickyRestart()` calls `persistence.restoreConfiguration()`, which returns
the full saved TOML, then passes it directly to `handleStart(configToml, host, port)`.

**After**: Add `wasRunning(): Boolean` to `DaemonPersistence` (reads only the `was_running`
boolean from plain prefs). `handleStickyRestart()` uses this to decide whether to restart at
all; if yes, it delegates to `handleStartFromSettings()` which always rebuilds TOML fresh from
current Room/DataStore state.

The saved TOML in `DaemonPersistence` continues to be written on each successful start (for a
future feature where the exact last-good config is needed), but it is no longer used for
auto-restart.

```
DaemonPersistence
  + fun wasRunning(): Boolean

ZeroClawDaemonService.handleStickyRestart()
  startForeground(...)
  if (!persistence.wasRunning()) { stopForeground; stopSelf; return }
  Log.i(TAG, "Rebuilding daemon config after process death")
  activityRepository.record(...)
  handleStartFromSettings()   ← replaces handleStart(saved.configToml, ...)
```

### Gap 2 — `markRestartRequired()` on key mutations (`ApiKeysViewModel`)

Inject `daemonBridge: DaemonServiceBridge` into `ApiKeysViewModel` via
`(application as ZeroClawApplication).daemonBridge`.

After each successful `addKey()`, `updateKey()`, `rotateKey()`, call
`daemonBridge.markRestartRequired()`. This triggers the existing restart banner in the UI
(same behaviour as settings changes).

### Gap 3 — Clear `defaultProvider` on `deleteKey()` (`ApiKeysViewModel`)

Inject `settingsRepository: SettingsRepository` into `ApiKeysViewModel` via
`(application as ZeroClawApplication).settingsRepository`.

In `deleteKey(id)`, look up the key before deletion. After successful deletion:

1. Resolve both the deleted key's provider and `settings.defaultProvider` through
   `ProviderRegistry.findById()` to canonical IDs for reliable comparison.
2. If they match, find the first remaining key in the store and call
   `settingsRepository.setDefaultProvider(remaining.provider)` and
   `settingsRepository.setDefaultModel("")`. If no keys remain, clear both fields to `""`.
3. Always call `daemonBridge.markRestartRequired()` after any key deletion.

`importCredentialsFile()` should also call `markRestartRequired()` on success (a new Anthropic
key was just stored; user should restart to pick it up).

### Gap 4 / 5 — OAuth refresh failure marks key invalid (`EncryptedApiKeyRepository`)

In `getByProviderFresh()`, when the OAuth token is expired and the refresh call throws or
returns an error:

- Call `save(key.copy(status = KeyStatus.INVALID))` to persist the invalid state.
- Return `null` instead of the stale expired token.

This has two effects:
- The UI shows a red badge on the key (existing `KeyStatus.INVALID` rendering).
- The daemon starts with `apiKey = null` for that provider. `ConfigTomlBuilder` emits no
  `api_key` line. Upstream Rust reports "credentials not set" at daemon start rather than a
  silent 500 on the first webhook call.

The user then knows exactly what to fix (re-import credentials) rather than seeing a generic 500.

## Files Changed

| File | Change |
|---|---|
| `app/…/service/DaemonPersistence.kt` | Add `wasRunning(): Boolean` |
| `app/…/service/ZeroClawDaemonService.kt` | `handleStickyRestart()` — use `wasRunning()`, call `handleStartFromSettings()` |
| `app/…/ui/screen/settings/apikeys/ApiKeysViewModel.kt` | Inject `daemonBridge` + `settingsRepository`; `markRestartRequired()` on all mutations; clear `defaultProvider` on `deleteKey()`; `markRestartRequired()` on `importCredentialsFile()` |
| `app/…/data/repository/EncryptedApiKeyRepository.kt` | On OAuth refresh failure → mark key `INVALID`, return `null` |

## Out of Scope

- Upstream gateway error differentiation (401 vs 500 vs network error) — requires Rust changes.
- Auto-restarting the daemon on key change — user preference confirmed: show banner only.
- UI for selecting a new default provider when the current one is deleted — the automatic
  selection of the first remaining key is sufficient for now.
