#!/usr/bin/env bash
# Reverts everything optimize.sh changed.
# Usage: ./restore.sh [TV_IP]     (default 192.168.0.169)
set -euo pipefail

TV_IP="${1:-192.168.0.169}"
TARGET="$TV_IP:5555"
STATE_FILE="$(dirname "$0")/.disabled-packages"

adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
tv() { adb -s "$TARGET" shell "$@"; }

echo "Restoring default animation times..."
tv settings put global window_animation_scale 1
tv settings put global transition_animation_scale 1
tv settings put global animator_duration_scale 1

if [[ -f "$STATE_FILE" ]]; then
  sort -u "$STATE_FILE" | while read -r pkg; do
    echo "Re-enabling $pkg"
    tv pm enable --user 0 "$pkg" >/dev/null || true
  done
  rm "$STATE_FILE"
fi

echo "Done."
