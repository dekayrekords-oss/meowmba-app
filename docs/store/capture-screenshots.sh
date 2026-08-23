#!/usr/bin/env bash
# Capture Play Store phone screenshots from the Fold cover display.
# USB must be connected. The inner screen is usually OFF and comes out black.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/screenshots"
mkdir -p "$OUT"

SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "No phone on adb. Plug in the Fold and unlock it."
  exit 1
fi

# Cover display on the Fold 7 (see docs/HANDOFF.md).
DISPLAY=4630946872173396372
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$OUT/cover-$STAMP.png"

adb -s "$SERIAL" exec-out screencap -p -d "$DISPLAY" > "$FILE"
echo "Saved $FILE"
echo "Need 4 shots: face, chat, tiles, privacy. Move this window, tap the next screen, run again."
