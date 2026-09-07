#!/usr/bin/env bash
# Builds the composeApp debug APK, boots an Android emulator (reusing one
# if already running), installs the APK, and launches the app.
#
# macOS only -- can't run inside the devcontainer: no working aapt2 there
# (see build-android-apk.sh), and no KVM/display for an emulator either.
#
# Usage: ./run-android-app.sh [avd-name]
#   avd-name defaults to the first AVD returned by `emulator -list-avds`.
set -euo pipefail

if [ -f /.dockerenv ] || [ "$(uname -s)" != "Darwin" ]; then
    echo "This script builds and runs the Android app and only works on macOS." >&2
    echo "It needs aapt2 (x86_64-only, no emulation here) and a real display" >&2
    echo "for the emulator -- neither works inside this container." >&2
    echo "Run this from a Terminal on the Mac instead." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

if [ ! -x "$EMULATOR" ] || [ ! -x "$ADB" ]; then
    echo "Can't find the Android SDK's emulator/adb under $ANDROID_HOME." >&2
    echo "Set ANDROID_HOME if your SDK lives elsewhere." >&2
    exit 1
fi

echo "==> Building the debug APK..."
./gradlew :composeApp:assembleDebug

APK_DIR="apps/composeApp/build/outputs/apk/debug"
APK="$(find "$APK_DIR" -name "*.apk" | head -1)"
if [ -z "$APK" ]; then
    echo "No APK found under $APK_DIR after the build." >&2
    exit 1
fi
echo "APK: $APK"

if "$ADB" devices | grep -q "^emulator-.*device$"; then
    echo "==> An emulator is already running, reusing it."
else
    AVD="${1:-$("$EMULATOR" -list-avds | head -1)}"
    if [ -z "$AVD" ]; then
        echo "No Android Virtual Device found." >&2
        echo "Create one first: Android Studio -> Tools -> Device Manager -> Create Device." >&2
        exit 1
    fi
    echo "==> Starting emulator '$AVD' (log: /tmp/android-emulator.log)..."
    nohup "$EMULATOR" -avd "$AVD" >/tmp/android-emulator.log 2>&1 &
    disown

    echo "==> Waiting for the emulator to come online (first boot can take a while)..."
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
    done
fi

echo "==> Installing the APK..."
"$ADB" install -r "$APK"

echo "==> Launching the app..."
"$ADB" shell am start -n org.iutools.app/org.iutools.app.MainActivity

echo "Done."
