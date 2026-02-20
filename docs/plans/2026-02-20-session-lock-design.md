# Session Lock (Bank-App Style) Design

**Date:** 2026-02-20
**Status:** Approved, pending implementation
**Target:** v0.0.20+

## Summary

Replace the scattered per-action biometric gates with a single app-wide session
lock. The app requires PIN (4-6 digits) or biometric authentication on launch
and after a configurable background timeout. Matches the UX pattern of banking
apps.

## Requirements

- **Full lockout:** Lock screen covers the entire app. Daemon continues running
  in the background but the UI is completely gated.
- **Auth methods:** App-specific PIN (mandatory baseline) + biometric
  (optional convenience). No device-credential fallback.
- **Auto-lock:** Configurable timeout after app goes to background (1, 5, 15,
  30 minutes). Default 15 minutes. Always lock on cold launch.
- **Replaces:** `biometricForService`, `biometricForSettings`, and all
  `BiometricSettingsGate` usages. Session auth subsumes per-action gates.

## Architecture

### SessionLockManager

Singleton held by `ZeroClawApplication`. Implements `DefaultLifecycleObserver`
and registers with `ProcessLifecycleOwner`.

**State:**
- `isLocked: StateFlow<Boolean>` -- starts true on cold launch
- `isLockEnabled: StateFlow<Boolean>` -- derived from settings
- `backgroundTimestamp: Long` -- captured in `onStop()`

**Lifecycle:**
- `onStop()` records `System.currentTimeMillis()`
- `onStart()` compares elapsed time against configured timeout
- If elapsed >= timeout, sets `isLocked = true`
- `unlock()` sets `isLocked = false` after successful auth

### PIN Storage

- PIN hashed with Argon2 and stored in `EncryptedSharedPreferences`
- Never stored in plaintext or logged
- No attempt limits (device-level BiometricPrompt lockout handles brute force)
- No "forgot PIN" flow -- user must clear app data (intentional for security)

### New AppSettings Fields

| Field | Type | Default |
|---|---|---|
| `lockEnabled` | Boolean | false |
| `lockTimeoutMinutes` | Int | 15 |
| `pinHash` | String | "" (empty = not set) |

### Removed Fields

| Field | Reason |
|---|---|
| `biometricForService` | Subsumed by session lock |
| `biometricForSettings` | Subsumed by session lock |

Dead keys remain in DataStore (silently ignored). No migration needed.

## Lock Gate UI

`LockGateScreen` composable sits above the `NavHost` in `ZeroClawAppShell`.
When `isLocked == true`, renders on top of all content.

**Layout:**
- App icon centered at top
- "ZeroClaw is locked" status text
- PIN keypad: 0-9 grid, backspace, 4-6 dot indicators
- Biometric button below keypad (if enrolled), auto-triggers on appear
- "Forgot PIN?" text linking to reset explanation dialog

**Animations (power-save aware):**
- Dots fill as digits entered
- Shake on wrong PIN (skipped if `isPowerSaveMode`)
- Dots turn green on success, gate dissolves

**Accessibility (TalkBack):**
- Lock screen announces "ZeroClaw is locked. Enter PIN to unlock." via
  `LiveRegionMode.Polite`
- Each digit button has content description
- Dot indicators: combined semantics "PIN entry, N of M digits entered",
  individual dots use `invisibleToUser()`
- Wrong PIN: "Incorrect PIN, try again" announced via Polite live region
- Correct PIN: "Unlocked" announced, focus moves to app content
- Lock gate sets `importantForAccessibility = noHideDescendants` on content
  behind it to prevent information leakage through the accessibility tree

## Onboarding Integration

**Permissions step** gets a "Security" section replacing the biometric toggles:

1. "Set up a PIN to protect this app" button opens PIN entry bottom sheet
   (enter + confirm, 4-6 digits enforced)
2. After PIN set, biometric toggle appears: "Also unlock with fingerprint/face"
3. Skipping is allowed -- lock is opt-in, can be enabled later in Settings

**Re-run wizard:** Shows "PIN is set" checkmark if already configured, with
"Change PIN" option.

## Settings Integration

Settings > Security Overview gets a new "App Lock" section at the top,
replacing the two biometric toggles:

- PIN setup/change (requires current PIN to change)
- Biometric toggle
- Timeout picker (1/5/15/30 min dropdown, default 15)
- Enable/disable lock toggle (requires current PIN to disable)

## Migration & Backwards Compatibility

- **Existing users:** `lockEnabled` defaults to false. No lock on update.
  Zero disruption. Opt in via Settings.
- **Dead fields:** `biometricForService`/`biometricForSettings` stay as dead
  DataStore keys. Silently ignored.
- **Edge case:** Users who had per-action biometric enabled lose that gate
  silently. Release notes should mention the change.
- **No Room migration needed** -- all affected fields are DataStore preferences.
- **No new dependencies** -- `ProcessLifecycleOwner` already available via
  `lifecycle-process`.

## Deleted Code

- `BiometricSettingsGate` composable (unused after migration)
- `BiometricProtectionSection` in `SecurityOverviewScreen`
- `biometricForService`/`biometricForSettings` from `AppSettings`
- `updateBiometricForService()`/`updateBiometricForSettings()` from
  `SettingsViewModel`
- `setBiometricForService()`/`setBiometricForSettings()` from
  `SettingsRepository` and `DataStoreSettingsRepository`
- Per-action biometric checks in `DaemonViewModel`
- Biometric toggles from `PermissionsStep` (replaced by PIN setup)

## Files Affected (Estimated)

**New:**
- `SessionLockManager.kt` (util)
- `LockGateScreen.kt` (ui/component)
- `PinEntrySheet.kt` (ui/component)
- `PinKeypad.kt` (ui/component)

**Modified:**
- `ZeroClawApplication.kt` -- hold SessionLockManager
- `ZeroClawAppShell.kt` -- wrap NavHost with LockGateScreen
- `AppSettings.kt` -- add lock fields, remove biometric fields
- `SettingsRepository.kt` -- add lock setters, remove biometric setters
- `DataStoreSettingsRepository.kt` -- same
- `SecurityOverviewScreen.kt` -- replace biometric section with app lock
- `PermissionsStep.kt` -- replace biometric toggles with PIN setup
- `OnboardingViewModel.kt` -- replace biometric state with PIN/lock state
- `OnboardingScreen.kt` -- update collector
- `DaemonViewModel.kt` -- remove per-action biometric checks
- `ZeroClawNavHost.kt` -- remove BiometricSettingsGate wrappers
- Test files for all of the above

**Deleted:**
- `BiometricSettingsGate.kt`
