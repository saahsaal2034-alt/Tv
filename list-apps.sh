#!/usr/bin/env bash
# Lists apps you installed on the TV, to help you pick ones for disable.txt.
# Usage: ./list-apps.sh [TV_IP]     (default 192.168.0.169)
set -euo pipefail
TARGET="${1:-192.168.0.169}:5555"
adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
adb -s "$TARGET" shell pm list packages -3 | tr -d '\r' | sed 's/^package://' | sort
