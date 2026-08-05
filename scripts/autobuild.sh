#!/usr/bin/env bash
# Auto-build: rebuilds the MEOWMBA APKs after every commit and drops them in
# build-out/ with the commit hash in the name. Invoked by .git/hooks/post-commit
# (runs in the background so commits never block). Also safe to run by hand.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1

SHA="$(git rev-parse --short HEAD)"
OUT="$PWD/build-out"
LOG="$OUT/autobuild-$SHA.log"
mkdir -p "$OUT"

notify() {
  osascript -e "display notification \"$1\" with title \"MEOWMBA build\"" 2>/dev/null || true
}

if ./gradlew :app:assembleDebug >"$LOG" 2>&1; then
  cp app/build/outputs/apk/debug/app-debug.apk "$OUT/meowmba-debug-$SHA.apk"
  echo "$(date '+%F %T') OK  debug  $SHA" >> "$OUT/autobuild.log"
  notify "debug APK ready: meowmba-debug-$SHA.apk"
else
  echo "$(date '+%F %T') FAIL debug  $SHA" >> "$OUT/autobuild.log"
  notify "BUILD FAILED at $SHA — see build-out/autobuild-$SHA.log"
  exit 1
fi

# Release APK too — release signing is configured via keystore.properties.
if ./gradlew :app:assembleRelease >>"$LOG" 2>&1; then
  cp app/build/outputs/apk/release/app-release.apk "$OUT/meowmba-release-$SHA.apk"
  echo "$(date '+%F %T') OK  release $SHA" >> "$OUT/autobuild.log"
else
  echo "$(date '+%F %T') FAIL release $SHA" >> "$OUT/autobuild.log"
fi

# Refresh the live handoff doc's build section so any future session can see
# what was built and when.
if [ -f docs/HANDOFF.md ]; then
  TMP="$(mktemp)"
  {
    echo "- $(date '+%F %T') `$SHA`: $(git log -1 --format=%s | head -c 100)"
    git log --format='- %h %ad %s' --date=format:'%Y-%m-%d %H:%M' -4 HEAD~1
  } > "$TMP"
  awk -v marker='<!-- AUTOBUILD:START -->' -v end='<!-- AUTOBUILD:END -->' -v inj="$TMP" '
    $0 == marker { print; print "(regenerated after each commit)"; while ((getline line < inj) > 0) print line; skip=1; next }
    $0 == end { skip=0 }
    !skip { print }
  ' docs/HANDOFF.md > docs/HANDOFF.md.new && mv docs/HANDOFF.md.new docs/HANDOFF.md
  rm -f "$TMP"
fi
