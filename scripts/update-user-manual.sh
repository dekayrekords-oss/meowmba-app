#!/usr/bin/env bash
# Regenerate the stamped Meowmba User Manual for a Preview ship.
#
# Source of truth: docs/USER_MANUAL.md
# Outputs:
#   app/src/main/assets/docs/meowmba-user-manual.pdf
#   app/src/main/assets/docs/meowmba-user-manual-build.txt
# Optional (when DEST_APK_SERVE is set or /home/prog/apk-serve exists):
#   $DEST_APK_SERVE/meowmba-user-manual.pdf
#
# Usage:
#   scripts/update-user-manual.sh
#   VERSION_CODE=130 VERSION_NAME=1.3.81 scripts/update-user-manual.sh
#   DEST_APK_SERVE=/home/prog/apk-serve scripts/update-user-manual.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SRC="$ROOT/docs/USER_MANUAL.md"
ASSETS_DIR="$ROOT/app/src/main/assets/docs"
OUT_PDF="$ASSETS_DIR/meowmba-user-manual.pdf"
OUT_STAMP="$ASSETS_DIR/meowmba-user-manual-build.txt"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/meow-manual.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f "$SRC" ]]; then
  echo "error: missing $SRC" >&2
  exit 1
fi

# Resolve stamp fields
if [[ -z "${VERSION_CODE:-}" ]]; then
  VERSION_CODE="$(
    python3 - <<'PY'
from pathlib import Path
import re
text = Path("app/build.gradle.kts").read_text()
# Prefer debug onVariants override
m = re.search(r'output\.versionCode\.set\((\d+)\)', text)
if m:
    print(m.group(1)); raise SystemExit
m = re.search(r'versionCode\s*=\s*(\d+)', text)
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
STAMP_DATE="$(TZ=America/Los_Angeles date '+%Y-%m-%d %H:%M %Z')"
PACKAGE_ID="com.example.meowmba"

mkdir -p "$ASSETS_DIR"

# Stamp markdown placeholders
python3 - "$SRC" "$WORK/stamped.md" "$VERSION_CODE" "$VERSION_NAME" "$GIT_SHA" "$STAMP_DATE" <<'PY'
import sys
from pathlib import Path
src, dest, vc, vn, sha, date = sys.argv[1:7]
text = Path(src).read_text()
repl = {
    "{{VERSION_CODE}}": vc,
    "{{VERSION_NAME}}": vn,
    "{{GIT_SHA}}": sha,
    "{{STAMP_DATE}}": date,
}
for k, v in repl.items():
    text = text.replace(k, v)
Path(dest).write_text(text)
print(f"stamped vc{vc} {vn} {sha} {date}")
PY

# Always write build stamp sidecar (readable without a PDF viewer)
cat > "$OUT_STAMP" <<STAMP
meowmba-user-manual
versionName=$VERSION_NAME
versionCode=$VERSION_CODE
gitSha=$GIT_SHA
packageId=$PACKAGE_ID
stampedAt=$STAMP_DATE
source=docs/USER_MANUAL.md
STAMP

# HTML intermediate for Chrome print (pandoc PDF engines often missing)
pandoc "$WORK/stamped.md" \
  -f markdown \
  -t html5 \
  --standalone \
  --metadata title="Meowmba User Manual" \
  -V lang=en \
  -o "$WORK/manual.html" \
  -H /dev/stdin <<'CSS'
<style>
  @page { margin: 18mm 16mm; }
  body {
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    font-size: 11.5pt;
    line-height: 1.45;
    color: #111;
    max-width: 48rem;
    margin: 0 auto;
  }
  h1 { font-size: 1.8rem; margin-top: 0; }
  h2 { font-size: 1.25rem; border-bottom: 1px solid #ddd; padding-bottom: 0.2em; margin-top: 1.6em; }
  h3 { font-size: 1.05rem; margin-top: 1.2em; }
  code, pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.9em; }
  blockquote { border-left: 3px solid #8b5cf6; margin-left: 0; padding: 0.4em 0.8em; background: #f6f3ff; }
  table { border-collapse: collapse; width: 100%; font-size: 0.92em; }
  th, td { border: 1px solid #ccc; padding: 4px 6px; vertical-align: top; }
</style>
CSS

CHROME_BIN=""
for c in google-chrome google-chrome-stable chromium chromium-browser; do
  if command -v "$c" >/dev/null 2>&1; then CHROME_BIN="$c"; break; fi
done

PDF_OK=0
if [[ -n "$CHROME_BIN" ]]; then
  # file:// URL for local HTML
  HTML_URL="file://$WORK/manual.html"
  if "$CHROME_BIN" --headless --disable-gpu --no-pdf-header-footer \
      --print-to-pdf="$WORK/manual.pdf" "$HTML_URL" >/tmp/meow-manual-chrome.log 2>&1; then
    if [[ -s "$WORK/manual.pdf" ]]; then
      cp -f "$WORK/manual.pdf" "$OUT_PDF"
      PDF_OK=1
      echo "wrote PDF via $CHROME_BIN → $OUT_PDF ($(wc -c < "$OUT_PDF") bytes)"
    fi
  else
    echo "warn: chrome print failed; see /tmp/meow-manual-chrome.log" >&2
  fi
fi

if [[ "$PDF_OK" -ne 1 ]]; then
  # Fallback: try pandoc with any available engine
  for engine in weasyprint wkhtmltopdf pdflatex xelatex lualatex; do
    if command -v "$engine" >/dev/null 2>&1; then
      if pandoc "$WORK/stamped.md" -o "$WORK/manual.pdf" --pdf-engine="$engine" 2>/tmp/meow-manual-pandoc.log; then
        cp -f "$WORK/manual.pdf" "$OUT_PDF"
        PDF_OK=1
        echo "wrote PDF via pandoc/$engine → $OUT_PDF"
        break
      fi
    fi
  done
fi

if [[ "$PDF_OK" -ne 1 ]]; then
  if [[ -f "$OUT_PDF" ]]; then
    echo "warn: could not regenerate PDF; keeping existing $OUT_PDF and refreshed stamp $OUT_STAMP" >&2
  else
    echo "error: no PDF engine available and no existing PDF at $OUT_PDF" >&2
    exit 2
  fi
fi

# Optional serve copy (local path or SSH later)
DEST_APK_SERVE="${DEST_APK_SERVE:-}"
if [[ -z "$DEST_APK_SERVE" && -d /home/prog/apk-serve ]]; then
  DEST_APK_SERVE=/home/prog/apk-serve
fi
if [[ -n "$DEST_APK_SERVE" && -d "$DEST_APK_SERVE" && -f "$OUT_PDF" ]]; then
  cp -f "$OUT_PDF" "$DEST_APK_SERVE/meowmba-user-manual.pdf"
  cp -f "$OUT_STAMP" "$DEST_APK_SERVE/meowmba-user-manual-build.txt"
  echo "copied to $DEST_APK_SERVE/meowmba-user-manual.pdf"
fi

echo "stamp file: $OUT_STAMP"
cat "$OUT_STAMP"
