#!/usr/bin/env bash
# Speeds up a Google TV (e.g. Xiaomi) over the local network using ADB.
# Usage: ./optimize.sh [TV_IP]     (default 192.168.0.169)
set -euo pipefail

TV_IP="${1:-192.168.0.169}"
TARGET="$TV_IP:5555"
DISABLE_LIST="$(dirname "$0")/disable.txt"
STATE_FILE="$(dirname "$0")/.disabled-packages"

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools first."; exit 1; }

echo "Connecting to $TARGET (accept the prompt on the TV if it appears)..."
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
tv() { adb -s "$TARGET" shell "$@"; }

echo "Model: $(tv getprop ro.product.model | tr -d '\r')"

echo "Halving UI animation times..."
tv settings put global window_animation_scale 0.5
tv settings put global transition_animation_scale 0.5
tv settings put global animator_duration_scale 0.5

echo "Clearing app caches..."
tv pm trim-caches 999G || true

echo "Stopping background apps..."
tv am kill-all || true

if [[ -f "$DISABLE_LIST" ]]; then
  installed="$(tv pm list packages | tr -d '\r' | sed 's/^package://')"
  while read -r pkg; do
    pkg="${pkg%%#*}"; pkg="${pkg// /}"
    [[ -z "$pkg" ]] && continue
    if grep -qx "$pkg" <<<"$installed"; then
      echo "Disabling $pkg"
      tv pm disable-user --user 0 "$pkg" >/dev/null && echo "$pkg" >>"$STATE_FILE"
    else
      echo "Skipping $pkg (not installed)"
    fi
  done <"$DISABLE_LIST"
fi

echo "Done. Restart the TV (hold power on the remote -> Restart) to finish."
echo "Undo everything with ./restore.sh $TV_IP"
