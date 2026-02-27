#!/bin/bash
set -uo pipefail

echo "=========================================="
echo "  ZeroClaw-Android Lint & Test Suite"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"

FAILED=0
PASSED=0
SKIPPED=0

run_step() {
    local name="$1"
    shift
    printf "%-30s" "$name"
    if "$@" > /tmp/zeroclaw-lint-output.txt 2>&1; then
        echo "PASS"
        PASSED=$((PASSED + 1))
    else
        echo "FAIL"
        cat /tmp/zeroclaw-lint-output.txt
        echo ""
        FAILED=$((FAILED + 1))
    fi
}

echo "--- Rust ---"
run_step "rustfmt"              cargo fmt -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml --check
run_step "clippy"               cargo clippy -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml --all-targets -- -D warnings
run_step "cargo-deny"           cargo deny --manifest-path zeroclaw-android/Cargo.toml check

echo ""
echo "--- Kotlin ---"
run_step "spotlessCheck"        ./gradlew spotlessCheck --quiet
run_step "detekt"               ./gradlew detekt --quiet

echo ""
echo "--- Unit Tests ---"
run_step "Rust tests"           cargo test -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml
run_step "Kotlin tests"         ./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest --quiet

echo ""
echo "--- Emulator Tests (optional) ---"
if adb devices 2>/dev/null | grep -q "device$"; then
    run_step "Build debug APK"  ./gradlew :app:assembleDebug --quiet
    run_step "Install APK"      adb install -r app/build/outputs/apk/debug/app-debug.apk
    run_step "Screen tests"     ./gradlew pixel7Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.zeroclaw.android.screen
    if command -v maestro &> /dev/null; then
        run_step "Maestro E2E" maestro test maestro/flows/ --exclude-tags real-daemon
    else
        echo "Maestro E2E                   SKIP (maestro not installed)"
        SKIPPED=$((SKIPPED + 1))
    fi
else
    echo "Screen tests                  SKIP (no emulator)"
    echo "Maestro E2E                   SKIP (no emulator)"
    SKIPPED=$((SKIPPED + 2))
fi

rm -f /tmp/zeroclaw-lint-output.txt

echo ""
echo "=========================================="
echo "  $PASSED passed, $FAILED failed, $SKIPPED skipped"
if [ "$FAILED" -eq 0 ]; then
    echo "  ALL CHECKS PASSED"
else
    echo "  $FAILED CHECK(S) FAILED"
fi
echo "=========================================="
exit "$FAILED"
