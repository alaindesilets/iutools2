#!/usr/bin/env bash
# Builds the composeApp Android APK. Must be run on the macOS host, not
# inside the devcontainer: the container's aarch64 Linux can't run Google's
# x86_64-only aapt2 binary (no working Rosetta/binfmt bridge in there), so
# ":composeApp:assembleDebug" always fails at the resource-compilation step.
# See AGENTS.md / project memory "android-build-blocker-devcontainer" for
# the full story.
set -euo pipefail

if [ -f /.dockerenv ] || [ "$(uname -s)" != "Darwin" ]; then
    echo "This script builds the Android APK and only works on macOS." >&2
    echo "You're running it inside a container (or non-macOS host) — the" >&2
    echo "container can't run aapt2 (x86_64-only, no emulation set up here)." >&2
    echo "Run this from a Terminal on the Mac instead." >&2
    exit 1
fi

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

APK_DIR="composeApp/build/outputs/apk/$BUILD_TYPE"
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
