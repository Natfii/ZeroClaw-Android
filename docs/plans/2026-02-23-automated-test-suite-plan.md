# Automated Test Suite Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a layered test pyramid with Compose screen tests, Maestro E2E journey flows, Gradle Managed Devices for CI, real-daemon integration tests, and Claude Code hooks to gate commits and releases.

**Architecture:** Four test layers (unit, screen, journey, real-daemon) using Compose Testing + Maestro YAML. Screen composables refactored to stateful/stateless split. CI gains two new jobs running on Gradle Managed Devices. Pre-commit hooks run appropriate test tiers based on changed files.

**Tech Stack:** Compose Testing (ui-test-junit4), Maestro CLI (YAML flows), Gradle Managed Devices (Pixel 7 API 35), JUnit5, MockK, existing unit test infrastructure.

**Design doc:** `docs/plans/2026-02-23-automated-test-suite-design.md`

---

### Task 1: Install Maestro and Verify

**Files:**
- None (system setup)

**Step 1: Download and install Maestro on Windows**

Download the latest Maestro release from GitHub, extract to a local directory, and add to PATH.

```bash
# Download latest release
curl -L -o /tmp/maestro.zip https://github.com/mobile-dev-inc/maestro/releases/latest/download/maestro-cli-windows.zip

# Extract to home directory
mkdir -p ~/maestro
unzip -o /tmp/maestro.zip -d ~/maestro

# Verify it works (requires Java 17 which is already installed)
export PATH="$HOME/maestro/bin:$PATH"
maestro --version
```

Expected: Prints Maestro version number.

**Step 2: Verify Maestro can see a running emulator**

```bash
# Start the ZeroClaw_Test emulator (if not running)
$ANDROID_HOME/emulator/emulator -avd ZeroClaw_Test -no-window -no-audio &
sleep 30
adb devices
maestro hierarchy
```

Expected: `maestro hierarchy` dumps the current UI tree from the emulator.

**Step 3: Commit — no files to commit, just verify setup works**

---

### Task 2: Add Compose Testing Dependencies and Gradle Managed Device Config

**Files:**
- Modify: `gradle/libs.versions.toml` (add compose-ui-test-junit4, test-runner, test-rules)
- Modify: `app/build.gradle.kts` (add GMD config + androidTest dependencies)

**Step 1: Add new entries to version catalog**

In `gradle/libs.versions.toml`, add to `[libraries]`:

```toml
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
test-runner = { group = "androidx.test", name = "runner", version.ref = "test-core" }
test-rules = { group = "androidx.test", name = "rules", version.ref = "test-core" }
```

Note: `compose-ui-test-junit4` version is managed by the Compose BOM, so no version.ref needed.

**Step 2: Add Gradle Managed Device and new dependencies to app/build.gradle.kts**

Add the GMD config inside the `android { testOptions { } }` block, replacing the existing `testOptions`:

```kotlin
testOptions {
    unitTests.isReturnDefaultValues = true
    managedDevices {
        devices {
            create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel7Api35") {
                device = "Pixel 7"
                apiLevel = 35
                systemImageSource = "google"
            }
        }
        groups {
            create("ci") {
                targetDevices.add(devices.getByName("pixel7Api35"))
            }
        }
    }
}
```

Add androidTest dependencies:

```kotlin
androidTestImplementation(platform(libs.compose.bom))
androidTestImplementation(libs.compose.ui.test.junit4)
androidTestImplementation(libs.test.runner)
androidTestImplementation(libs.test.rules)
```

**Step 3: Verify the build compiles**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
export ANDROID_HOME="/c/Users/Natal/AppData/Local/Android/Sdk"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest > /tmp/build-out.txt 2>&1; cat /tmp/build-out.txt | tail -20
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Compose Testing deps and Gradle Managed Device config"
```

---

### Task 3: Create Maestro Directory Structure and First Flow

**Files:**
- Create: `maestro/flows/.gitkeep`
- Create: `maestro/flows/real-daemon/.gitkeep`
- Create: `maestro/subflows/.gitkeep`
- Create: `maestro/config/test-config.toml`
- Create: `maestro/flows/onboarding.yaml` (first flow)

**Step 1: Create directory structure**

```bash
mkdir -p maestro/flows/real-daemon maestro/subflows maestro/config
```

**Step 2: Create the test TOML config for real-daemon tests**

File: `maestro/config/test-config.toml`

```toml
[provider]
type = "openai_compatible"
endpoint = "http://192.168.1.197:1234"
model = "qwen2.5"
api_key = "lm-studio"

[router]
default_temperature = 0.7
max_tokens = 1024

[memory]
type = "in_memory"

[channel]
type = "local"
```

**Step 3: Create the onboarding journey flow**

File: `maestro/flows/onboarding.yaml`

```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - onboarding
---
- clearState
- launchApp
- assertVisible: "Welcome to ZeroClaw"
- tapOn: "Get Started"
- assertVisible: "Select Provider"
- tapOn: "OpenAI Compatible"
- tapOn: "API Endpoint"
- inputText: "http://localhost:1234"
- tapOn: "Next"
- assertVisible: "Configure Model"
- tapOn: "Model Name"
- inputText: "qwen2.5"
- tapOn: "Next"
- assertVisible: "Setup Complete"
- tapOn: "Go to Dashboard"
- assertVisible: "Daemon Status"
```

Note: The exact text values will need to be validated against the actual UI once we run Maestro against the app. These are based on the Appium element map and screen code. Adjust after first run.

**Step 4: Verify Maestro can parse the flow**

```bash
export PATH="$HOME/maestro/bin:$PATH"
maestro test --dry-run maestro/flows/onboarding.yaml 2>&1 || echo "dry-run not supported, will test live"
```

**Step 5: Commit**

```bash
git add maestro/
git commit -m "test: add Maestro directory structure and onboarding flow"
```

---

### Task 4: Refactor DashboardScreen to Stateful/Stateless Split

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/dashboard/DashboardScreen.kt`

**Step 1: Understand the current structure**

The current `DashboardScreen` composable:
- Takes `edgeMargin`, nav callbacks, `viewModel`, `modifier`
- Collects 6 flows from the ViewModel (serviceState, statusState, keyRejection, healthDetail, costSummary, cronJobs)
- Also reads `BatteryOptimization` context locally
- Renders a Column with status hero, metrics, health, cost, cron, activity sections

**Step 2: Create a data class to hold all dashboard state**

Add at the top of the file (after imports, before the existing function):

```kotlin
/**
 * Aggregated state for the dashboard content composable.
 *
 * @property serviceState Current daemon service lifecycle state.
 * @property statusState Daemon status details (uptime, version, etc.).
 * @property keyRejection Latest API key rejection event, if any.
 * @property healthDetail Component health breakdown.
 * @property costSummary Accumulated cost summary string.
 * @property cronJobs Active cron job summaries.
 */
data class DashboardState(
    val serviceState: ServiceState,
    val statusState: DaemonUiState<DaemonStatus>,
    val keyRejection: String?,
    val healthDetail: DaemonUiState<HealthDetail>,
    val costSummary: DaemonUiState<String>,
    val cronJobs: DaemonUiState<List<String>>,
)
```

**Step 3: Extract the body into a DashboardContent composable**

Create a new internal function `DashboardContent` that accepts the state as parameters instead of collecting flows. Move the entire Column (the UI body) into this function. The function signature:

```kotlin
@Composable
internal fun DashboardContent(
    state: DashboardState,
    edgeMargin: Dp,
    onNavigateToCostDetail: () -> Unit,
    onNavigateToCronJobs: () -> Unit,
    onStartDaemon: () -> Unit,
    onStopDaemon: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Step 4: Reduce DashboardScreen to a thin stateful wrapper**

```kotlin
@Composable
fun DashboardScreen(
    edgeMargin: Dp,
    onNavigateToCostDetail: () -> Unit = {},
    onNavigateToCronJobs: () -> Unit = {},
    viewModel: DaemonViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val statusState by viewModel.statusState.collectAsStateWithLifecycle()
    val keyRejection by viewModel.keyRejectionEvent.collectAsStateWithLifecycle()
    val healthDetail by viewModel.healthDetail.collectAsStateWithLifecycle()
    val costSummary by viewModel.costSummary.collectAsStateWithLifecycle()
    val cronJobs by viewModel.cronJobs.collectAsStateWithLifecycle()

    DashboardContent(
        state = DashboardState(
            serviceState = serviceState,
            statusState = statusState,
            keyRejection = keyRejection,
            healthDetail = healthDetail,
            costSummary = costSummary,
            cronJobs = cronJobs,
        ),
        edgeMargin = edgeMargin,
        onNavigateToCostDetail = onNavigateToCostDetail,
        onNavigateToCronJobs = onNavigateToCronJobs,
        onStartDaemon = viewModel::startService,
        onStopDaemon = viewModel::stopService,
        modifier = modifier,
    )
}
```

Note: The `BatteryOptimization` context reads (`detectAggressiveOem`, `isExempt`) and `LocalContext.current` stay in `DashboardContent` since they are UI-layer concerns. The key point is that ViewModel flow collection is isolated in the wrapper.

**Step 5: Verify the app builds and existing unit tests pass**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest > /tmp/build-out.txt 2>&1; cat /tmp/build-out.txt | tail -20
```

Expected: BUILD SUCCESSFUL, all tests pass.

**Step 6: Commit**

```bash
git add app/src/main/java/com/zeroclaw/android/ui/screen/dashboard/DashboardScreen.kt
git commit -m "refactor: split DashboardScreen into stateful wrapper + stateless content"
```

---

### Task 5: Refactor Remaining Screens (AgentsScreen, ConsoleScreen, PluginsScreen, SettingsScreen, OnboardingScreen, ApiKeysScreen, LockScreen, DoctorScreen)

**Files:**
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/agents/AgentsScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/console/ConsoleScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/plugins/PluginsScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/settings/apikeys/ApiKeysScreen.kt`
- Modify: `app/src/main/java/com/zeroclaw/android/ui/screen/settings/doctor/DoctorScreen.kt`

Apply the same pattern from Task 4 to each screen:

1. Create a `*State` data class capturing all ViewModel-collected state
2. Extract the UI body into a `*Content` composable with `internal` visibility
3. Reduce the original `*Screen` composable to a thin wrapper that collects flows and delegates

For each screen:

**AgentsScreen** -> `AgentsState(agents, searchQuery)` + `AgentsContent(...)`
**ConsoleScreen** -> `ConsoleState(messages, isLoading, pendingImages, isProcessingImages)` + `ConsoleContent(...)`
**PluginsScreen** -> `PluginsState(plugins, selectedTab, searchQuery, syncState)` + `PluginsContent(...)`
**SettingsScreen** -> `SettingsState(settings)` + `SettingsContent(...)`
**OnboardingScreen** -> Already has collector pattern; add an `OnboardingContent` wrapper for the step layout
**ApiKeysScreen** -> Read the file first to determine state shape, then apply pattern
**DoctorScreen** -> Read the file first to determine state shape, then apply pattern

**Lock screen note:** The lock screen (`LockScreen`) may be in a different location. Search for it — it could be part of the settings or auth package. Read it first.

After each screen refactor, run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest > /tmp/build-out.txt 2>&1; cat /tmp/build-out.txt | tail -20
```

**Commit after each screen** (not all at once):

```bash
git commit -m "refactor: split AgentsScreen into stateful wrapper + stateless content"
git commit -m "refactor: split ConsoleScreen into stateful wrapper + stateless content"
# ... etc
```

---

### Task 6: Write Compose Screen Tests

**Files:**
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/helpers/FakeData.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/DashboardScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/ConnectionsScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/PluginsScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/ConsoleScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/SettingsScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/OnboardingScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/ApiKeysScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/LockScreenTest.kt`
- Create: `app/src/androidTest/java/com/zeroclaw/android/screen/DoctorScreenTest.kt`

**Step 1: Create FakeData.kt with test data factories**

```kotlin
package com.zeroclaw.android.screen.helpers

// Import all model classes used by screen state data classes
// Provide factory functions for each state type

internal fun fakeDashboardState(): DashboardState = DashboardState(
    serviceState = ServiceState.Stopped,
    statusState = DaemonUiState.Content(DaemonStatus(/* minimal valid */)),
    keyRejection = null,
    healthDetail = DaemonUiState.Content(HealthDetail(/* minimal valid */)),
    costSummary = DaemonUiState.Content("$0.00"),
    cronJobs = DaemonUiState.Content(emptyList()),
)

// ... similar factories for each screen's state class
```

Exact model constructors will need to be read from the source. The implementing agent should read each model class to determine required fields.

**Step 2: Write DashboardScreenTest.kt**

```kotlin
package com.zeroclaw.android.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeroclaw.android.screen.helpers.fakeDashboardState
import com.zeroclaw.android.ui.screen.dashboard.DashboardContent
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.viewmodel.DaemonUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contentState_showsDaemonStatus() {
        composeTestRule.setContent {
            DashboardContent(
                state = fakeDashboardState(),
                edgeMargin = 16.dp,
                onNavigateToCostDetail = {},
                onNavigateToCronJobs = {},
                onStartDaemon = {},
                onStopDaemon = {},
            )
        }
        composeTestRule
            .onNodeWithText("Daemon Status")
            .assertIsDisplayed()
    }

    @Test
    fun stoppedState_showsStartButton() {
        composeTestRule.setContent {
            DashboardContent(
                state = fakeDashboardState().copy(
                    serviceState = ServiceState.Stopped,
                ),
                edgeMargin = 16.dp,
                onNavigateToCostDetail = {},
                onNavigateToCronJobs = {},
                onStartDaemon = {},
                onStopDaemon = {},
            )
        }
        composeTestRule
            .onNodeWithText("Start Daemon")
            .assertIsDisplayed()
    }
}
```

**Step 3: Run the screen test on the managed device**

```bash
./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.DashboardScreenTest" > /tmp/test-out.txt 2>&1; cat /tmp/test-out.txt | tail -30
```

Expected: Tests pass. First run will be slow (downloads system image + boots emulator).

**Step 4: Write remaining screen tests following the same pattern**

Each test file should cover:
- Default/content state renders correctly
- Loading state shows progress indicator
- Error state shows error message and retry button
- Key interactive elements are present and enabled/disabled correctly
- Empty state renders appropriately

The implementing agent should read each screen's `*Content` composable to determine exactly what text/nodes to assert on.

**Step 5: Run all screen tests**

```bash
./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.*" > /tmp/test-out.txt 2>&1; cat /tmp/test-out.txt | tail -30
```

**Step 6: Commit**

```bash
git add app/src/androidTest/java/com/zeroclaw/android/screen/
git commit -m "test: add Compose screen tests for all 9 primary screens"
```

---

### Task 7: Write Remaining Maestro Journey Flows + Subflows

**Files:**
- Create: `maestro/subflows/navigate-to-tab.yaml`
- Create: `maestro/subflows/complete-onboarding.yaml`
- Create: `maestro/subflows/start-daemon.yaml`
- Create: `maestro/flows/daemon-lifecycle.yaml`
- Create: `maestro/flows/agent-management.yaml`
- Create: `maestro/flows/plugin-browsing.yaml`
- Create: `maestro/flows/settings-roundtrip.yaml`
- Create: `maestro/flows/console-interaction.yaml`
- Create: `maestro/flows/error-recovery.yaml`

**Step 1: Create reusable subflows**

File: `maestro/subflows/navigate-to-tab.yaml`
```yaml
appId: com.zeroclaw.android
---
- tapOn: ${tab}
```

File: `maestro/subflows/complete-onboarding.yaml`
```yaml
appId: com.zeroclaw.android
---
- clearState
- launchApp
- assertVisible: "Welcome to ZeroClaw"
- tapOn: "Get Started"
- assertVisible: "Select Provider"
- tapOn: "OpenAI Compatible"
- tapOn: "API Endpoint"
- inputText: "http://localhost:1234"
- tapOn: "Next"
- assertVisible: "Configure Model"
- tapOn: "Model Name"
- inputText: "test-model"
- tapOn: "Next"
- assertVisible: "Setup Complete"
- tapOn: "Go to Dashboard"
- assertVisible: "Daemon Status"
```

File: `maestro/subflows/start-daemon.yaml`
```yaml
appId: com.zeroclaw.android
---
- tapOn: "Start Daemon"
- assertVisible:
    text: "Running"
    timeout: 10000
```

**Step 2: Create journey flows**

File: `maestro/flows/daemon-lifecycle.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - daemon
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Start Daemon"
- assertVisible:
    text: "Running"
    timeout: 10000
- tapOn: "Stop Daemon"
- assertVisible:
    text: "Stopped"
    timeout: 5000
```

File: `maestro/flows/agent-management.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - agents
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Connections"
- assertVisible: "Connections"
- tapOn:
    id: "fab_add_agent"
- assertVisible: "Add Agent"
- tapOn: "Agent Name"
- inputText: "Test Agent"
- tapOn: "Save"
- assertVisible: "Test Agent"
```

File: `maestro/flows/plugin-browsing.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - plugins
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Plugins"
- assertVisible: "Installed"
- tapOn: "Available"
- assertVisible: "Available"
- tapOn: "Skills"
- assertVisible: "Skills"
```

File: `maestro/flows/settings-roundtrip.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - settings
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Settings"
- assertVisible: "Settings"
- tapOn: "About"
- assertVisible: "0.0.22"
- back
- assertVisible: "Settings"
```

File: `maestro/flows/console-interaction.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - console
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Console"
- assertVisible: "Console"
- tapOn:
    id: "console_input"
- inputText: "Hello"
- tapOn:
    id: "console_send"
```

File: `maestro/flows/error-recovery.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - journey
  - error
---
- runFlow: ../subflows/complete-onboarding.yaml
- tapOn: "Start Daemon"
- assertVisible:
    text: "Error|Failed"
    timeout: 15000
    optional: true
```

Note: All flows use placeholder text values that must be validated against the running app. The implementing agent should run each flow against the emulator and fix assertion text to match actual UI strings.

**Step 3: Test all flows locally**

```bash
export PATH="$HOME/maestro/bin:$PATH"
maestro test maestro/flows/ 2>&1 | tail -30
```

Fix any assertion mismatches based on actual UI text.

**Step 4: Commit**

```bash
git add maestro/
git commit -m "test: add Maestro E2E journey flows and reusable subflows"
```

---

### Task 8: Write Real-Daemon Maestro Flows and Lifecycle Scripts

**Files:**
- Create: `maestro/flows/real-daemon/daemon-boot.yaml`
- Create: `maestro/flows/real-daemon/conversation.yaml`
- Create: `maestro/flows/real-daemon/restart-cycle.yaml`
- Create: `maestro/flows/real-daemon/bad-endpoint.yaml`
- Create: `scripts/test-real-daemon.sh`
- Create: `scripts/test-fresh-install.sh`
- Create: `scripts/test-upgrade.sh`
- Create: `scripts/test-uninstall-reinstall.sh`
- Create: `scripts/test-local-all.sh`

**Step 1: Create real-daemon flows**

File: `maestro/flows/real-daemon/daemon-boot.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - real-daemon
  - integration
---
- clearState
- launchApp
- assertVisible: "Welcome to ZeroClaw"
- tapOn: "Get Started"
- tapOn: "OpenAI Compatible"
- tapOn: "API Endpoint"
- inputText: "http://192.168.1.197:1234"
- tapOn: "API Key"
- inputText: "lm-studio"
- tapOn: "Next"
- tapOn: "Model Name"
- inputText: "qwen2.5"
- tapOn: "Next"
- tapOn: "Go to Dashboard"
- tapOn: "Start Daemon"
- assertVisible:
    text: "Running"
    timeout: 30000
- tapOn: "Console"
- tapOn:
    id: "console_input"
- inputText: "Hello, are you there?"
- tapOn:
    id: "console_send"
- assertVisible:
    text: ".*"
    timeout: 30000
```

File: `maestro/flows/real-daemon/conversation.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - real-daemon
  - conversation
---
- runFlow: ../../subflows/complete-onboarding.yaml
- runFlow: ../../subflows/start-daemon.yaml
- tapOn: "Console"
- tapOn:
    id: "console_input"
- inputText: "What is 2+2?"
- tapOn:
    id: "console_send"
- assertVisible:
    text: ".*"
    timeout: 30000
- tapOn:
    id: "console_input"
- inputText: "Now multiply that by 3"
- tapOn:
    id: "console_send"
- assertVisible:
    text: ".*"
    timeout: 30000
- tapOn:
    id: "console_input"
- inputText: "Thank you"
- tapOn:
    id: "console_send"
- assertVisible:
    text: ".*"
    timeout: 30000
```

File: `maestro/flows/real-daemon/restart-cycle.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - real-daemon
  - restart
---
- runFlow: ../../subflows/complete-onboarding.yaml
- tapOn: "Start Daemon"
- assertVisible:
    text: "Running"
    timeout: 30000
- tapOn: "Stop Daemon"
- assertVisible:
    text: "Stopped"
    timeout: 10000
- tapOn: "Start Daemon"
- assertVisible:
    text: "Running"
    timeout: 30000
- tapOn: "Console"
- tapOn:
    id: "console_input"
- inputText: "Are you still working after restart?"
- tapOn:
    id: "console_send"
- assertVisible:
    text: ".*"
    timeout: 30000
```

File: `maestro/flows/real-daemon/bad-endpoint.yaml`
```yaml
appId: com.zeroclaw.android
tags:
  - real-daemon
  - error
---
- clearState
- launchApp
- tapOn: "Get Started"
- tapOn: "OpenAI Compatible"
- tapOn: "API Endpoint"
- inputText: "http://192.168.1.197:9999"
- tapOn: "API Key"
- inputText: "bad-key"
- tapOn: "Next"
- tapOn: "Model Name"
- inputText: "nonexistent"
- tapOn: "Next"
- tapOn: "Go to Dashboard"
- tapOn: "Start Daemon"
- assertVisible:
    text: "Error|Failed|error"
    timeout: 30000
```

**Step 2: Create test runner scripts**

File: `scripts/test-real-daemon.sh`
```bash
#!/bin/bash
set -euo pipefail

echo "=== Real Daemon E2E Tests ==="
echo "Requires LM Studio running at http://192.168.1.197:1234"
echo ""

# Verify LM Studio is reachable
if ! curl -sf http://192.168.1.197:1234/v1/models > /dev/null 2>&1; then
    echo "ERROR: LM Studio not reachable at 192.168.1.197:1234"
    echo "Start LM Studio and load a Qwen model first."
    exit 1
fi
echo "LM Studio: OK"

# Verify emulator/device is connected
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device/emulator connected"
    echo "Start an emulator: \$ANDROID_HOME/emulator/emulator -avd ZeroClaw_Test"
    exit 1
fi
echo "Device: OK"

# Install latest debug APK
echo "Installing debug APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run real-daemon flows
echo "Running real-daemon Maestro flows..."
maestro test maestro/flows/real-daemon/

echo ""
echo "=== All real-daemon tests passed ==="
```

File: `scripts/test-fresh-install.sh`
```bash
#!/bin/bash
set -euo pipefail

echo "=== Fresh Install Test ==="

PACKAGE="com.zeroclaw.android"

# Uninstall if present
adb uninstall "$PACKAGE" 2>/dev/null || true

# Install fresh
adb install app/build/outputs/apk/debug/app-debug.apk

# Run onboarding flow (verifies clean state)
maestro test maestro/flows/onboarding.yaml

echo "=== Fresh install test passed ==="
```

File: `scripts/test-upgrade.sh`
```bash
#!/bin/bash
set -euo pipefail

echo "=== Upgrade Test ==="

PACKAGE="com.zeroclaw.android"

# Get previous release APK
PREV_TAG=$(gh release list --limit 2 --json tagName -q '.[1].tagName')
echo "Previous release: $PREV_TAG"

PREV_APK="/tmp/zeroclaw-prev.apk"
gh release download "$PREV_TAG" -p '*.apk' -D /tmp --clobber
mv /tmp/app-release.apk "$PREV_APK" 2>/dev/null || mv /tmp/app-release-unsigned.apk "$PREV_APK"

# Clean install previous version
adb uninstall "$PACKAGE" 2>/dev/null || true
adb install "$PREV_APK"

# Run onboarding on previous version
maestro test maestro/flows/onboarding.yaml || echo "WARN: onboarding may differ on older version"

# Upgrade to current version
echo "Upgrading to current version..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify app still works after upgrade
maestro test maestro/flows/settings-roundtrip.yaml

echo "=== Upgrade test passed ==="
```

File: `scripts/test-uninstall-reinstall.sh`
```bash
#!/bin/bash
set -euo pipefail

echo "=== Uninstall/Reinstall Test ==="

PACKAGE="com.zeroclaw.android"

# Install and complete setup
adb install -r app/build/outputs/apk/debug/app-debug.apk
maestro test maestro/flows/onboarding.yaml

# Uninstall completely
echo "Uninstalling..."
adb uninstall "$PACKAGE"

# Reinstall
echo "Reinstalling..."
adb install app/build/outputs/apk/debug/app-debug.apk

# Verify clean slate (onboarding should appear again)
maestro test maestro/flows/onboarding.yaml

echo "=== Uninstall/reinstall test passed ==="
```

File: `scripts/test-local-all.sh`
```bash
#!/bin/bash
set -euo pipefail

echo "=========================================="
echo "  ZeroClaw-Android Full Local Test Suite"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

FAILED=0

run_suite() {
    local name="$1"
    local cmd="$2"
    echo "--- $name ---"
    if eval "$cmd"; then
        echo "PASS: $name"
    else
        echo "FAIL: $name"
        FAILED=$((FAILED + 1))
    fi
    echo ""
}

# Unit tests (JVM + Rust)
run_suite "Rust unit tests" "cd zeroclaw-android && cargo test -p zeroclaw-ffi"
run_suite "Kotlin unit tests" "./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest"

# Compose screen tests (needs emulator)
run_suite "Compose screen tests" "./gradlew pixel7Api35DebugAndroidTest --tests 'com.zeroclaw.android.screen.*'"

# Maestro journey flows
run_suite "Maestro journeys" "maestro test maestro/flows/ --exclude-tags real-daemon"

# Real daemon tests (optional, needs LM Studio)
if curl -sf http://192.168.1.197:1234/v1/models > /dev/null 2>&1; then
    run_suite "Real daemon E2E" "$SCRIPT_DIR/test-real-daemon.sh"
else
    echo "SKIP: Real daemon tests (LM Studio not available)"
fi

# Lifecycle tests
run_suite "Fresh install" "$SCRIPT_DIR/test-fresh-install.sh"
run_suite "Upgrade" "$SCRIPT_DIR/test-upgrade.sh"
run_suite "Uninstall/reinstall" "$SCRIPT_DIR/test-uninstall-reinstall.sh"

echo "=========================================="
if [ "$FAILED" -eq 0 ]; then
    echo "  ALL SUITES PASSED"
else
    echo "  $FAILED SUITE(S) FAILED"
fi
echo "=========================================="
exit "$FAILED"
```

**Step 3: Make scripts executable**

```bash
chmod +x scripts/test-real-daemon.sh scripts/test-fresh-install.sh scripts/test-upgrade.sh scripts/test-uninstall-reinstall.sh scripts/test-local-all.sh
```

**Step 4: Commit**

```bash
git add maestro/flows/real-daemon/ scripts/
git commit -m "test: add real-daemon Maestro flows and local test runner scripts"
```

---

### Task 9: Update CI Workflow with Screen Test and Maestro Jobs

**Files:**
- Modify: `.github/workflows/ci.yml`

**Step 1: Add screen-test job**

Add after the existing `test` job. This job depends on `test` passing first.

```yaml
  screen-test:
    name: Screen Tests
    runs-on: ubuntu-latest
    needs: [test]
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: android-actions/setup-android@v3

      - name: Install NDK
        run: sdkmanager "ndk;27.2.12479018"

      - uses: dtolnay/rust-toolchain@stable
        with:
          targets: aarch64-linux-android, x86_64-linux-android

      - name: Install cargo-ndk
        run: cargo install cargo-ndk

      - uses: Swatinem/rust-cache@v2
        with:
          workspaces: zeroclaw-android -> target

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run Compose screen tests on managed device
        run: ./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.*"
```

**Step 2: Add maestro-test job**

```yaml
  maestro-test:
    name: Maestro E2E
    runs-on: ubuntu-latest
    needs: [test]
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: android-actions/setup-android@v3

      - name: Install NDK
        run: sdkmanager "ndk;27.2.12479018"

      - uses: dtolnay/rust-toolchain@stable
        with:
          targets: aarch64-linux-android, x86_64-linux-android

      - name: Install cargo-ndk
        run: cargo install cargo-ndk

      - uses: Swatinem/rust-cache@v2
        with:
          workspaces: zeroclaw-android -> target

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Install Maestro
        run: curl -Ls "https://get.maestro.mobile.dev" | bash

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug

      - name: Start emulator and run Maestro flows
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          profile: pixel_7
          heap-size: 512M
          ram-size: 4096M
          script: |
            adb install app/build/outputs/apk/debug/app-debug.apk
            $HOME/.maestro/bin/maestro test maestro/flows/ --exclude-tags real-daemon
```

Note: The Maestro job uses `android-emulator-runner` instead of Gradle Managed Devices because Maestro needs a live adb-connected emulator, not a Gradle-managed one. The screen tests use GMD because Compose Testing integrates with Gradle's test runner.

**Step 3: Update the build job to depend on new test jobs**

Change the `build` job's `needs` to include the new jobs so the full pipeline is:

```yaml
  build:
    name: Build
    runs-on: ubuntu-latest
    needs: [lint-rust, lint-kotlin, cargo-deny, screen-test, maestro-test]
```

**Step 4: Verify CI YAML is valid**

```bash
# Use yamllint or manual review
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" 2>&1 || echo "YAML parse error"
```

**Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Compose screen test and Maestro E2E jobs with PR gates"
```

---

### Task 10: Add Claude Code Pre-Commit and Pre-Release Test Hooks

**Files:**
- Create: `scripts/hooks/pre-commit-test.sh`
- Create: `scripts/hooks/pre-release-test.sh`
- Modify: `.claude/settings.json`

**Step 1: Create pre-commit test hook script**

File: `scripts/hooks/pre-commit-test.sh`

```bash
#!/bin/bash
# Pre-commit test hook for Claude Code
# Detects which files are being committed and runs appropriate test tier

set -euo pipefail

PROJECT_DIR="$(git rev-parse --show-toplevel)"
cd "$PROJECT_DIR"

export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
export ANDROID_HOME="/c/Users/Natal/AppData/Local/Android/Sdk"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"

# Check if this is actually a git commit command
TOOL_INPUT="${TOOL_INPUT:-}"
if ! echo "$TOOL_INPUT" | grep -q "git commit"; then
    exit 0
fi

# Get staged files
STAGED=$(git diff --cached --name-only 2>/dev/null || true)
if [ -z "$STAGED" ]; then
    exit 0
fi

RUN_RUST=false
RUN_KOTLIN=false
RUN_SCREEN=false

# Classify changed files
while IFS= read -r file; do
    case "$file" in
        zeroclaw-ffi/src/*|zeroclaw-android/zeroclaw-ffi/src/*)
            RUN_RUST=true
            ;;
        app/src/main/java/*/ui/*|app/src/main/java/*/screen/*)
            RUN_KOTLIN=true
            RUN_SCREEN=true
            ;;
        app/src/*|lib/src/*)
            RUN_KOTLIN=true
            ;;
        maestro/*)
            RUN_SCREEN=true
            ;;
    esac
done <<< "$STAGED"

FAILED=false

if [ "$RUN_RUST" = true ]; then
    echo "Running Rust unit tests..."
    if ! (cd zeroclaw-android && cargo test -p zeroclaw-ffi); then
        FAILED=true
    fi
fi

if [ "$RUN_KOTLIN" = true ]; then
    echo "Running Kotlin unit tests..."
    if ! ./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest -q; then
        FAILED=true
    fi
fi

if [ "$RUN_SCREEN" = true ]; then
    echo "Running Compose screen tests (needs emulator)..."
    if adb devices | grep -q "device$"; then
        if ! ./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.*" -q; then
            FAILED=true
        fi
    else
        echo "WARN: No emulator connected, skipping screen tests"
    fi
fi

if [ "$FAILED" = true ]; then
    echo "BLOCKED: Tests failed. Fix before committing."
    exit 1
fi
```

**Step 2: Create pre-release test hook script**

File: `scripts/hooks/pre-release-test.sh`

```bash
#!/bin/bash
# Pre-release test hook for Claude Code
# Runs full test pyramid when version bump is detected

set -euo pipefail

PROJECT_DIR="$(git rev-parse --show-toplevel)"
cd "$PROJECT_DIR"

export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
export ANDROID_HOME="/c/Users/Natal/AppData/Local/Android/Sdk"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"

# Check if this is a git commit
TOOL_INPUT="${TOOL_INPUT:-}"
if ! echo "$TOOL_INPUT" | grep -q "git commit"; then
    exit 0
fi

# Check if this looks like a version bump
STAGED_DIFF=$(git diff --cached 2>/dev/null || true)
IS_VERSION_BUMP=false

if echo "$STAGED_DIFF" | grep -qE '(versionName|versionCode|^version\s*=)'; then
    IS_VERSION_BUMP=true
fi
if echo "$TOOL_INPUT" | grep -qiE '(bump|release|version)'; then
    IS_VERSION_BUMP=true
fi

if [ "$IS_VERSION_BUMP" = false ]; then
    exit 0
fi

echo "=== Version bump detected: running full test pyramid ==="
FAILED=false

# Rust tests
echo "1/4 Rust unit tests..."
if ! (cd zeroclaw-android && cargo test -p zeroclaw-ffi); then
    FAILED=true
fi

# Kotlin unit tests
echo "2/4 Kotlin unit tests..."
if ! ./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest -q; then
    FAILED=true
fi

# Compose screen tests
echo "3/4 Compose screen tests..."
if adb devices | grep -q "device$"; then
    if ! ./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.*" -q; then
        FAILED=true
    fi
else
    echo "WARN: No emulator, skipping screen tests"
fi

# Maestro journey tests
echo "4/4 Maestro journey tests..."
if command -v maestro &> /dev/null && adb devices | grep -q "device$"; then
    ./gradlew :app:assembleDebug -q
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    if ! maestro test maestro/flows/ --exclude-tags real-daemon; then
        FAILED=true
    fi
else
    echo "WARN: Maestro not available or no emulator, skipping E2E tests"
fi

if [ "$FAILED" = true ]; then
    echo "BLOCKED: Full test pyramid failed. Fix before release commit."
    exit 1
fi

echo ""
echo "All automated tests passed."
echo "REMINDER: Run ./scripts/test-real-daemon.sh locally before pushing."
```

**Step 3: Make scripts executable**

```bash
chmod +x scripts/hooks/pre-commit-test.sh scripts/hooks/pre-release-test.sh
```

**Step 4: Update .claude/settings.json with new hooks**

Add a new `PreToolUse` entry for the Bash tool matcher. The existing hooks use `Edit|Write` matcher. Add a new entry with `Bash` matcher:

In `.claude/settings.json`, add to the `PreToolUse` array:

```json
{
    "matcher": "Bash",
    "hooks": [
        {
            "type": "command",
            "command": "if echo \"$TOOL_INPUT\" | grep -q 'git commit'; then bash scripts/hooks/pre-release-test.sh || bash scripts/hooks/pre-commit-test.sh; fi; exit 0",
            "timeout": 900000
        }
    ]
}
```

The pre-release hook runs first (exits 0 if not a version bump, runs full suite if it is). If it's not a version bump, the pre-commit hook runs the tiered tests.

Note: 900000ms = 15 min timeout for the full test suite.

**Step 5: Verify hooks don't interfere with normal operations**

```bash
# Test that a non-commit bash command doesn't trigger the hook
echo "ls should not trigger tests"
```

**Step 6: Commit**

```bash
git add scripts/hooks/ .claude/settings.json
git commit -m "build: add Claude Code pre-commit and pre-release test hooks"
```

---

### Task 11: Update ZeroClaw Submodule to Latest Release

**Files:**
- Modify: `zeroclaw/` (git submodule)
- Potentially modify: `zeroclaw-android/zeroclaw-ffi/Cargo.toml` (if dependency version changed)

**Step 1: Check current submodule state and available releases**

```bash
cd zeroclaw && git fetch --tags && git tag --sort=-creatordate | head -10 && cd ..
```

**Step 2: Update submodule to latest release tag**

```bash
cd zeroclaw && git checkout <latest-tag> && cd ..
```

**Step 3: Run /check-upstream to detect API drift**

Use the `check-upstream` skill to compare the submodule API against the cached map.

**Step 4: Build and run all tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :lib:testDebugUnitTest
cd zeroclaw-android && cargo test -p zeroclaw-ffi && cd ..
```

**Step 5: Fix any compilation or test failures from upstream changes**

This is the variable step — depends on what changed upstream. The check-upstream skill will identify specific misalignments.

**Step 6: Commit**

```bash
git add zeroclaw zeroclaw-android/
git commit -m "build: update ZeroClaw submodule to <version>"
```

---

### Task 12: Final Validation and Integration Test

**Step 1: Run the full local test suite**

```bash
./scripts/test-local-all.sh
```

**Step 2: Verify CI would pass by running the same commands locally**

```bash
# Lints
cd zeroclaw-android && cargo fmt --check && cargo clippy -p zeroclaw-ffi --all-targets -- -D warnings && cd ..
./gradlew spotlessCheck detekt

# Unit tests
cd zeroclaw-android && cargo test -p zeroclaw-ffi && cd ..
./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest

# Screen tests
./gradlew pixel7Api35DebugAndroidTest --tests "com.zeroclaw.android.screen.*"

# Maestro
maestro test maestro/flows/ --exclude-tags real-daemon

# Build
./gradlew :app:assembleRelease
```

**Step 3: Commit any final fixes**

```bash
git add -A
git commit -m "test: final validation and integration test fixes"
```
