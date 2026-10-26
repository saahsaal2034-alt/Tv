#!/usr/bin/env bash
# Brings back the stock Google TV home screen and removes the custom launcher.
# Usage: ./uninstall-launcher.sh [TV_IP]
set -euo pipefail

TARGET="${1:-192.168.0.169}:5555"
STOCK_LAUNCHER="com.google.android.apps.tv.launcherx"

adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
tv() { adb -s "$TARGET" shell "$@"; }

tv pm enable --user 0 "$STOCK_LAUNCHER" || true
tv cmd package set-home-activity "$STOCK_LAUNCHER/.MainActivity" || true
adb -s "$TARGET" uninstall com.tv.launcher || true
tv input keyevent KEYCODE_HOME
echo "Stock home screen restored."
