#!/bin/bash
# Pushes a built hansard.db to a connected device/emulator's app-specific
# external storage, where NunavutHansardLocalIndex.kt expects to find it --
# see tools/README-hansard.md for the full one-time setup this is one step
# of. Deliberately NOT part of the Gradle build/install tasks: this ~550 MiB
# file doesn't change on every code change, so wiring it into installDebug
# would push it on every redeploy too -- exactly what this whole local-index
# design (vs. bundling it as an app asset) was meant to avoid.
set -euo pipefail

APP_ID="org.iutools.app"
DB_PATH="${1:-tools/hansard.db}"

if [ ! -f "$DB_PATH" ]; then
    echo "ERROR: $DB_PATH not found -- run tools/build_hansard_index.py first (see tools/README-hansard.md)." >&2
    exit 1
fi

DEST="/sdcard/Android/data/$APP_ID/files/hansard.db"
echo "Pushing $DB_PATH ($(du -h "$DB_PATH" | cut -f1)) to $DEST ..."
adb push "$DB_PATH" "$DEST"
echo "Done -- re-run this after regenerating the DB, once per device/emulator."
