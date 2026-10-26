#!/usr/bin/env bash
# Installs the custom launcher and makes it the TV's home screen.
# Usage: ./install-launcher.sh path/to/launcher-release.apk [TV_IP]
set -euo pipefail

APK="${1:?Pass the path to launcher-release.apk}"
TARGET="${2:-192.168.0.169}:5555"
STOCK_LAUNCHER="com.google.android.apps.tv.launcherx"

adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
tv() { adb -s "$TARGET" shell "$@"; }

echo "Installing launcher..."
if ! adb -s "$TARGET" install -r "$APK"; then
  # Each build is signed with a new key, so replace the old copy.
  # Favorites and hidden apps are reset when this happens.
  adb -s "$TARGET" uninstall com.tv.launcher || true
  adb -s "$TARGET" install "$APK"
fi

echo "Setting it as the home screen..."
tv cmd package set-home-activity com.tv.launcher/.MainActivity || true

echo "Turning off the stock Google TV home screen..."
tv pm disable-user --user 0 "$STOCK_LAUNCHER" || true

tv input keyevent KEYCODE_HOME
echo "Done. Put the old home screen back with ./uninstall-launcher.sh"
