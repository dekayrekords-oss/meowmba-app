#!/usr/bin/env bash
# Write apk-serve preview-version.json for the current Preview ship.
#
# Usage (on prog host or with DEST_APK_SERVE pointed at a local mirror):
#   VERSION_CODE=131 VERSION_NAME=1.3.81 \
#   APK=meowmba-preview-vc131.apk APK_ALT=meowmba-preview-vc131-alt.apk \
#   DEST_APK_SERVE=/home/prog/apk-serve scripts/write-preview-version.sh
#
# Computes sha256 + size from the APK files when present. Also accepts
# SHA256 / SIZE / SHA256_ALT / SIZE_ALT env overrides.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEST="${DEST_APK_SERVE:-${APK_SERVE:-}}"
if [[ -z "$DEST" ]]; then
  if [[ -d /home/prog/apk-serve ]]; then
    DEST=/home/prog/apk-serve
  else
    DEST="$ROOT/build/apk-serve"
    mkdir -p "$DEST"
  fi
fi

if [[ -z "${VERSION_CODE:-}" ]]; then
  VERSION_CODE="$(
    python3 - <<'PY'
from pathlib import Path
import re
text = Path("app/build.gradle.kts").read_text()
m = re.search(r'output\.versionCode\.set\((\d+)\)', text)
print(m.group(1) if m else "0")
PY
  )"
fi
if [[ -z "${VERSION_NAME:-}" ]]; then
  VERSION_NAME="$(
    python3 - <<'PY'
from pathlib import Path
import re
text = Path("app/build.gradle.kts").read_text()
m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
print(m.group(1) if m else "0.0.0")
PY
  )"
fi

GIT_SHA="$(git rev-parse --short=7 HEAD 2>/dev/null || echo unknown)"
PUBLISHED_AT="$(TZ=America/Los_Angeles date -Iseconds)"
APK="${APK:-meowmba-preview-vc${VERSION_CODE}.apk}"
APK_ALT="${APK_ALT:-meowmba-preview-vc${VERSION_CODE}-alt.apk}"
NOTES="${NOTES:-Preview vc${VERSION_CODE} (${GIT_SHA})}"

hash_file() {
  local f="$1"
  if [[ -f "$f" ]]; then
    sha256sum "$f" | awk '{print $1}'
  else
    echo ""
  fi
}
size_file() {
  local f="$1"
  if [[ -f "$f" ]]; then
    stat -c%s "$f" 2>/dev/null || wc -c < "$f"
  else
    echo "0"
  fi
}

APK_PATH="$DEST/$APK"
ALT_PATH="$DEST/$APK_ALT"
SHA256="${SHA256:-$(hash_file "$APK_PATH")}"
SIZE="${SIZE:-$(size_file "$APK_PATH")}"
SHA256_ALT="${SHA256_ALT:-$(hash_file "$ALT_PATH")}"
SIZE_ALT="${SIZE_ALT:-$(size_file "$ALT_PATH")}"

OUT="$DEST/preview-version.json"
python3 - "$OUT" "$VERSION_CODE" "$VERSION_NAME" "$APK" "$SHA256" "$SIZE" \
  "$NOTES" "$PUBLISHED_AT" "$APK_ALT" "$SHA256_ALT" "$SIZE_ALT" <<'PY'
import json, sys
from pathlib import Path
out, vc, vn, apk, sha, size, notes, pub, apk_alt, sha_alt, size_alt = sys.argv[1:12]
payload = {
    "versionCode": int(vc),
    "versionName": vn,
    "apk": apk,
    "sha256": sha,
    "size": int(size or 0),
    "notes": notes,
    "publishedAt": pub,
}
if apk_alt:
    payload["apkAlt"] = apk_alt
if sha_alt:
    payload["sha256Alt"] = sha_alt
if size_alt and int(size_alt) > 0:
    payload["sizeAlt"] = int(size_alt)
Path(out).write_text(json.dumps(payload, indent=2) + "\n")
print(f"wrote {out} vc{vc} apk={apk} size={size}")
PY
