#!/bin/bash
set -euo pipefail

echo "=========================================="
echo "  ZeroClaw-Android Full Local Test Suite"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"

LM_STUDIO_HOST="${LM_STUDIO_HOST:-localhost:1234}"
export LM_STUDIO_HOST

FAILED=0

run_suite() {
    local name="$1"
    shift
    echo "--- $name ---"
    if "$@"; then
        echo "PASS: $name"
    else
        echo "FAIL: $name"
        FAILED=$((FAILED + 1))
    fi
    echo ""
}

# Retry wrapper for flaky E2E suites (emulator timing)
run_suite_with_retry() {
    local name="$1"
    shift
    echo "--- $name ---"
    if "$@"; then
        echo "PASS: $name"
    else
        echo "RETRY: $name (retrying once after 10s cooldown)"
        adb shell am force-stop com.zeroclaw.android 2>/dev/null || true
        sleep 10
        if "$@"; then
            echo "PASS: $name (on retry)"
        else
            echo "FAIL: $name"
            FAILED=$((FAILED + 1))
        fi
    fi
    echo ""
}

# Unit tests (JVM + Rust)
run_suite "Rust unit tests" $HOME/.cargo/bin/cargo.exe test -p zeroclaw-ffi --manifest-path "$PROJECT_DIR/zeroclaw-android/Cargo.toml"
run_suite "Kotlin unit tests" ./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest

# Compose screen tests (needs emulator)
if adb devices 2>/dev/null | grep -q "device$"; then
    run_suite "Compose screen tests" ./gradlew pixel7Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.zeroclaw.android.screen
else
    echo "SKIP: Compose screen tests (no emulator connected)"
fi

# Cooldown after Compose screen tests (let emulator settle)
sleep 5

# Maestro journey flows
if command -v maestro &> /dev/null && adb devices 2>/dev/null | grep -q "device$"; then
    run_suite_with_retry "Maestro journeys" maestro test maestro/flows/ --exclude-tags real-daemon
else
    echo "SKIP: Maestro journeys (maestro not available or no emulator)"
fi

# Real daemon tests (optional, needs LM Studio)
if curl -sf "http://$LM_STUDIO_HOST/v1/models" > /dev/null 2>&1; then
    run_suite_with_retry "Real daemon E2E" bash "$SCRIPT_DIR/test-real-daemon.sh"
else
    echo "SKIP: Real daemon tests (LM Studio not available)"
fi

# Lifecycle tests (need emulator)
if adb devices 2>/dev/null | grep -q "device$"; then
    run_suite "Fresh install" bash "$SCRIPT_DIR/test-fresh-install.sh"
    run_suite "Uninstall/reinstall" bash "$SCRIPT_DIR/test-uninstall-reinstall.sh"
else
    echo "SKIP: Lifecycle tests (no emulator connected)"
fi

echo "=========================================="
if [ "$FAILED" -eq 0 ]; then
    echo "  ALL SUITES PASSED"
else
    echo "  $FAILED SUITE(S) FAILED"
fi
echo "=========================================="
exit "$FAILED"
