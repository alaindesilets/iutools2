#!/usr/bin/env bash
# Builds the composeApp Android APK and prints the path to it.
#
# Works both on the macOS host and inside the (now x86_64) devcontainer --
# the old "macOS only" restriction is gone: that was for the earlier aarch64
# container, which couldn't run Google's x86_64-only aapt2. The current
# devcontainer image bundles a working Android SDK (ANDROID_HOME), so
# ":composeApp:assembleDebug" runs fine in it. The debug APK is signed with
# apps/composeApp/debug.keystore -- a fixed keystore committed to the repo
# (see signingConfigs.debug in apps/composeApp/build.gradle.kts) -- so every
# debug
# build has the same signature regardless of machine/container, and installs
# over the previous one without an uninstall.
set -euo pipefail

BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
    debug) GRADLE_TASK=":composeApp:assembleDebug" ;;
    release) GRADLE_TASK=":composeApp:assembleRelease" ;;
    *)
        echo "Usage: $0 [debug|release]" >&2
        exit 1
        ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

./gradlew "$GRADLE_TASK"

APK_DIR="apps/composeApp/build/outputs/apk/$BUILD_TYPE"
echo
echo "Build complete. APK(s):"
while IFS= read -r apk; do
    renamed="$(dirname "$apk")/iutools-${BUILD_TYPE}.apk"
    if [ "$apk" != "$renamed" ]; then
        mv "$apk" "$renamed"
        apk="$renamed"
    fi
    echo "  $apk"
done < <(find "$APK_DIR" -name "*.apk")
