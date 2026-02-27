#!/bin/bash
set -euo pipefail

# ZeroClaw-Android release script
# Usage: bash scripts/release.sh <new_version>
# Example: bash scripts/release.sh 0.0.27

if [ $# -ne 1 ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 0.0.27"
    exit 1
fi

NEW_VERSION="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$PATH"

# Extract current version from Cargo.toml
OLD_VERSION=$(grep '^version' zeroclaw-android/zeroclaw-ffi/Cargo.toml | head -1 | sed 's/.*"\(.*\)".*/\1/')
OLD_CODE=$(grep 'versionCode' app/build.gradle.kts | head -1 | sed 's/[^0-9]//g')
NEW_CODE=$((OLD_CODE + 1))

echo "=========================================="
echo "  ZeroClaw-Android Release"
echo "  $OLD_VERSION -> $NEW_VERSION"
echo "  versionCode $OLD_CODE -> $NEW_CODE"
echo "=========================================="
echo ""

# Sanity check — allow re-run if partially bumped
if [ "$OLD_VERSION" = "$NEW_VERSION" ]; then
    echo "NOTE: Cargo.toml already at $NEW_VERSION (partial bump from prior run)"
    echo "      Will ensure all other locations match."
    echo ""
fi

if ! git diff --quiet HEAD 2>/dev/null; then
    echo "Working tree has changes — they will be included in the release commit."
    echo ""
fi

# --- Version bumps ---
echo "--- Bumping versions ---"

# 1. app/build.gradle.kts (versionCode + versionName)
# Use regex to replace ANY current versionCode/versionName
sed -i "s/versionCode = [0-9]*/versionCode = $NEW_CODE/" app/build.gradle.kts
sed -i "s/versionName = \"[0-9.]*\"/versionName = \"$NEW_VERSION\"/" app/build.gradle.kts
echo "  app/build.gradle.kts: versionCode=$NEW_CODE, versionName=$NEW_VERSION"

# 2. lib/build.gradle.kts (publication version — only the maven line)
sed -i "/groupId\|artifactId/!s/version = \"[0-9.]*\"/version = \"$NEW_VERSION\"/" lib/build.gradle.kts
echo "  lib/build.gradle.kts: version=$NEW_VERSION"

# 3. zeroclaw-ffi/Cargo.toml
sed -i "s/^version = \"[0-9.]*\"/version = \"$NEW_VERSION\"/" zeroclaw-android/zeroclaw-ffi/Cargo.toml
echo "  zeroclaw-ffi/Cargo.toml: version=$NEW_VERSION"

# 4. zeroclaw-ffi/src/lib.rs (test assertion)
sed -i "s/assert_eq!(version, \"[0-9.]*\")/assert_eq!(version, \"$NEW_VERSION\")/" zeroclaw-android/zeroclaw-ffi/src/lib.rs
echo "  zeroclaw-ffi/src/lib.rs: test assertion=$NEW_VERSION"

# 5. Cargo.lock (regenerate)
echo "  Updating Cargo.lock..."
cd zeroclaw-android && cargo update -p zeroclaw-ffi --quiet && cd ..
echo "  Cargo.lock updated"

# 6. Fix line endings mangled by sed on Windows
echo "  Reformatting gradle files (spotlessApply)..."
./gradlew spotlessApply --quiet 2>/dev/null || true
echo "  Reformatting Rust files (rustfmt)..."
cargo fmt -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml 2>/dev/null || true
echo "  Done"

echo ""

# --- Verify versions match ---
echo "--- Verifying version sync ---"
CARGO_V=$(grep '^version' zeroclaw-android/zeroclaw-ffi/Cargo.toml | head -1 | sed 's/.*"\(.*\)".*/\1/')
LIB_V=$(grep 'version = ' lib/build.gradle.kts | grep -v 'java\|jvm\|kotlin\|sdk\|Version' | head -1 | sed 's/.*"\(.*\)".*/\1/')
APP_V=$(grep 'versionName' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
TEST_V=$(grep 'assert_eq!(version' zeroclaw-android/zeroclaw-ffi/src/lib.rs | sed 's/.*"\(.*\)".*/\1/')

MISMATCH=0
for v in "$CARGO_V" "$LIB_V" "$APP_V" "$TEST_V"; do
    if [ "$v" != "$NEW_VERSION" ]; then
        echo "  MISMATCH: expected $NEW_VERSION, got $v"
        MISMATCH=1
    fi
done
if [ "$MISMATCH" -eq 1 ]; then
    echo "ERROR: Version mismatch detected, aborting"
    exit 1
fi
echo "  All 4 locations: $NEW_VERSION"
echo ""

# --- Run lints ---
echo "--- Running lints ---"
LINT_FAILED=0

printf "  %-25s" "spotlessCheck"
if ./gradlew spotlessCheck --quiet > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

printf "  %-25s" "detekt"
if ./gradlew detekt --quiet > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

printf "  %-25s" "clippy"
if cargo clippy -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml --all-targets -- -D warnings > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

printf "  %-25s" "rustfmt"
if cargo fmt -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml --check > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

printf "  %-25s" "Rust tests"
if cargo test -p zeroclaw-ffi --manifest-path zeroclaw-android/Cargo.toml > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

printf "  %-25s" "Kotlin tests"
if ./gradlew :app:testDebugUnitTest :lib:testDebugUnitTest --quiet > /tmp/zc-release-lint.txt 2>&1; then
    echo "PASS"
else
    echo "FAIL"
    cat /tmp/zc-release-lint.txt
    LINT_FAILED=1
fi

rm -f /tmp/zc-release-lint.txt
echo ""

if [ "$LINT_FAILED" -ne 0 ]; then
    echo "ERROR: Lints/tests failed, aborting release"
    exit 1
fi

# --- Stage and commit ---
echo "--- Staging files ---"
git add \
    app/build.gradle.kts \
    lib/build.gradle.kts \
    zeroclaw-android/zeroclaw-ffi/Cargo.toml \
    zeroclaw-android/zeroclaw-ffi/src/ \
    zeroclaw-android/Cargo.lock

# Stage any other modified tracked files
git add -u

# Stage new files that should be included
git add scripts/lint-all.sh 2>/dev/null || true
git add app/src/main/java/com/zeroclaw/android/ui/screen/settings/EmbeddingRoutesScreen.kt 2>/dev/null || true
git add app/src/main/java/com/zeroclaw/android/ui/screen/settings/SecurityAdvancedScreen.kt 2>/dev/null || true

echo "  Staged $(git diff --cached --stat | tail -1)"
echo ""

echo "--- Committing ---"
git commit -m "$(cat <<EOF
feat(settings): add web fetch, web search, security, proxy, and embedding settings

Expand SettingsRepository with 40 new methods covering web fetch tool,
web search tool, security sandbox/resources/audit/OTP/e-stop, Qdrant
vector DB, embedding routes, query classification, and proxy config.

New screens: EmbeddingRoutesScreen, SecurityAdvancedScreen.
Updated all 3 test doubles with missing interface methods.
Fix detekt ComplexCondition in ConfigTomlBuilder.
Add scripts/lint-all.sh for local CI-equivalent lint+test runs.
Make CI patch step resilient to missing patches directory.
Add skip-emu-tests input to CI workflow for faster releases.
Bump submodule to latest upstream (patches no longer needed).
Bump version to v${NEW_VERSION}.

BREAKING CHANGE: SettingsRepository has 40 new abstract members.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
echo ""

# --- Tag and push ---
echo "--- Tagging v${NEW_VERSION} ---"
git tag "v${NEW_VERSION}"
echo ""

echo "--- Pushing ---"
git push origin main --tags
echo ""

echo "=========================================="
echo "  Release v${NEW_VERSION} pushed!"
echo "  CI + Release workflow will run on GitHub."
echo "  Watch: https://github.com/Natfii/ZeroClaw-Android/actions"
echo "=========================================="
