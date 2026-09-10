#!/usr/bin/env bash
# Local owner setup only; never exposed as a phone RPC or agent shell tool.
set -euo pipefail
bridge_adb="${MEOWMBA_ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
bridge_port="${1:-8765}"
if [[ ! "$bridge_port" =~ ^[0-9]{1,5}$ ]] || ((10#$bridge_port < 1 || 10#$bridge_port > 65535)); then
  echo 'Usage: connect_desktop_usb.sh [helper-port 1..65535]' >&2
  exit 2
fi
if [[ ! -x "$bridge_adb" ]]; then
  echo 'Android adb is missing. Set MEOWMBA_ADB to its executable path.' >&2
  exit 1
fi
# -d refuses to silently choose a Wi-Fi device or one of several USB phones.
"$bridge_adb" -d reverse "tcp:$bridge_port" "tcp:$bridge_port"
"$bridge_adb" -d reverse --list
echo 'USB tunnel ready. Open Meow Desktop Bridge settings and tap Ping.'
echo 'Pairing is unchanged. Re-run after the cable or adb connection resets.'
