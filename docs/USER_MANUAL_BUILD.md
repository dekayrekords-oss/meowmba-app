# User Manual — Preview build refresh

**Source of truth:** `docs/USER_MANUAL.md`  
**Bundled asset:** `app/src/main/assets/docs/meowmba-user-manual.pdf`  
**Stamp sidecar:** `app/src/main/assets/docs/meowmba-user-manual-build.txt`  
**Serve copy:** `/home/prog/apk-serve/meowmba-user-manual.pdf`

## Every Preview ship

```bash
# After bumping debug versionCode in app/build.gradle.kts:
scripts/update-user-manual.sh
# Optional explicit fields:
# VERSION_CODE=130 VERSION_NAME=1.3.81 DEST_APK_SERVE=/home/prog/apk-serve scripts/update-user-manual.sh
```

The script stamps **versionName, versionCode, git SHA, date** into the cover, regenerates
the PDF (Chrome headless print from pandoc HTML; falls back to pandoc PDF engines when
present), writes the sidecar stamp file, and copies to apk-serve when that directory exists.

If Chrome/pandoc PDF tooling is missing on a host, the stamp file is still updated and any
existing PDF is kept — restore tooling before claiming a fresh manual.

Setup **Hi.** opens the **current** bundled PDF (asset → cache → FileProvider / ArtifactViewer).


## Preview version feed (update check)

Every Preview ship must also publish the machine-readable feed the app polls:

```bash
VERSION_CODE=133 DEST_APK_SERVE=/home/prog/apk-serve scripts/write-preview-version.sh
```

Writes `$DEST_APK_SERVE/preview-version.json` with `versionCode`, `versionName`,
`apk`, `sha256`, `size`, optional `notes` / `publishedAt`, and alt fields when
the Claude-signed APK is present.

App Settings → **Help & feedback** → **Check for update** fetches
`{apk-serve}/preview-version.json` (default NetBird `http://100.77.97.236:8765`,
optional LAN fallback). A daily WorkManager / on-resume check notifies once per
new `versionCode`.
